/*
 * Copyright ActionML, LLC under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * ActionML licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.actionml.router.http.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directive, Directives, Route}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.actionml.core.validate._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import scaldi.Injector
import scaldi.akka.AkkaInjectable

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
  * 29.01.17 16:15
  */
abstract class BaseRouter(implicit inj: Injector) extends AkkaInjectable with FailFastCirceSupport with Directives {

  type Response = Validated[ValidateError, Json]

  implicit protected val actorSystem: ActorSystem = inject[ActorSystem]
  implicit protected val executor: ExecutionContext = actorSystem.dispatcher
  implicit protected val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(5 seconds)
  val route: Route
  protected val putOrPost: Directive[Unit] = post | put
  protected val asJson: Directive[Tuple1[Json]] = putOrPost & entity(as[Json])

  def completeByCond(
    ifDefinedStatus: StatusCode,
    ifEmptyStatus: StatusCode
  )(ifDefinedResource: Future[Option[Json]]): Route =
    onSuccess(ifDefinedResource) {
      case Some(json) => complete(ifDefinedStatus, json)
      case None => complete(ifEmptyStatus, ifEmptyStatus.defaultMessage())
    }

  def completeByValidated(
    ifDefinedStatus: StatusCode
  )(ifDefinedResource: Future[Response]): Route =
    onSuccess(ifDefinedResource) {
      case Valid(json) => complete(ifDefinedStatus, json)
      case Invalid(error: ParseError) => complete(StatusCodes.BadRequest, error.message)
      case Invalid(error: MissingParams) => complete(StatusCodes.BadRequest, error.message)
      case Invalid(error: WrongParams) => complete(StatusCodes.BadRequest, error.message)
      case Invalid(error: EventOutOfSequence) ⇒ complete(StatusCodes.BadRequest, error.message)
      case Invalid(error: NotImplemented) ⇒ complete(StatusCodes.NotImplemented, error.message)
      case Invalid(error: ResourceNotFound) ⇒ complete(StatusCodes.custom(404, "Resource not found"), error.message)
      case _ ⇒ complete(StatusCodes.NotFound)
    }

}
