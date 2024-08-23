import sbt._

object AppDependencies {

  private val bootstrapVersion = "9.3.0"
  

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
    "org.scalatestplus"       %% "mockito-4-11"               % "3.2.17.0"                  % Test
  )

  val it = Seq.empty
}
