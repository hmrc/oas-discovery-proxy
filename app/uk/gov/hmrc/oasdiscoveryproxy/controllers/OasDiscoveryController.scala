/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.oasdiscoveryproxy.controllers

import org.apache.pekko.util.{ByteString, CompactByteString}
import play.api.Logging
import play.api.http.{ContentTypes, HttpEntity}
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.oasdiscoveryproxy.service.AuthorizationDecorator
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class OasDiscoveryController @Inject()(
                                override val controllerComponents: ControllerComponents,
                                httpClient: HttpClientV2,
                                servicesConfig: ServicesConfig,
                                authorizationDecorator: AuthorizationDecorator)(implicit ec: ExecutionContext) extends BackendController(controllerComponents) with Logging {

  implicit class HttpClientExtensions(httpClient: HttpClientV2) {
    def httpVerb(method: String, relativePath: String)(implicit hc: HeaderCarrier): RequestBuilder = {
      val url = url"${s"$targetUrl$relativePath"}"

      method match {
        case "GET" => httpClient.get(url)
        case "POST" => httpClient.post(url)
        case "PUT" => httpClient.put(url)
        case "DELETE" => httpClient.delete(url)
        case "PATCH" => httpClient.patch(url)
        case "OPTIONS" => httpClient.options(url)
        case "HEAD" => httpClient.head(url)
        case _ => throw new IllegalArgumentException(s"No such verb $method")
      }
    }
  }

  def forward: Action[ByteString] = Action(parse.byteString).async {
    implicit request =>
      var builder = httpClient
        .httpVerb(request.method, request.path.replaceFirst("/oas-discovery-proxy", ""))

      request.headers.get(CONTENT_TYPE) match {
        case Some(ContentTypes.JSON) =>
          builder = builder.withBody(Json.parse(request.body.toArray))
        case _ =>
          builder = builder.withBody(request.body)
      }

      if (request.headers.hasHeader(ACCEPT)) {
        builder = builder.setHeader((ACCEPT, request.headers.get(ACCEPT).get))
      }

      builder = builder.transform(wsRequest => authorizationDecorator.decorate(wsRequest, request.headers.get(AUTHORIZATION)))

      builder.execute[HttpResponse]
        .map(
          response =>
            Result(
              ResponseHeader(
                status = response.status,
                headers = buildHeaders(response.headers)
              ),
              body = buildBody(response.body, response.headers)
            )
        )
  }

  private def buildBody(body: String, headers: Map[String, Seq[String]]): HttpEntity = {
    if (body.isEmpty) {
      HttpEntity.NoEntity
    }
    else {
      HttpEntity.Strict(CompactByteString(body), buildContentType(headers))
    }
  }


  private def buildContentType(headers: Map[String, Seq[String]]): Option[String] = {
    headers
      .find(_._1.equalsIgnoreCase("content-type"))
      .map(_._2.head)
  }


  private def buildHeaders(headers: Map[String, Seq[String]]): Map[String, String] = {
    headers
      .map {
        case (header, values) => (header, values.head)
      }
      .filter {
        case (header, _) if header.equalsIgnoreCase("content-type") => false
        case (header, _) if header.equalsIgnoreCase("content-length") => false
        case _ => true
      }
  }

  private def targetUrl: String = {
    val baseUrl = servicesConfig.baseUrl(s"oas-discovery-api")
    val path = servicesConfig.getConfString(s"oas-discovery-api.path", "")

    if (path.isEmpty) {
      s"$baseUrl"
    }
    else {
      s"$baseUrl/$path"
    }
  }
}
