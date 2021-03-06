package ch.octo.cffpoc.controllers

import javax.inject.Inject

import akka.actor.{ Actor, ActorLogging, ActorSystem, PoisonPill, Props }
import akka.event.Logging
import akka.stream.{ Materializer, OverflowStrategy }
import akka.stream.OverflowStrategy._
import akka.stream.scaladsl.{ Keep, Sink, Source, SourceQueue }
import ch.octo.cffpoc.ScheduleEnvironment
import ch.octo.cffpoc.Serializers._
import ch.octo.cffpoc.gtfs.raw.RawCalendarDateReader
import ch.octo.cffpoc.gtfs.simulator._
import ch.octo.cffpoc.gtfs.simulator.actors.ActorSimulatedTrips
import ch.octo.cffpoc.gtfs.simulator.actors.SimulatorMessages.StopSimulation
import ch.octo.cffpoc.gtfs._
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.http.ContentTypes
import play.api.libs.EventSource
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc._

import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * Created by alex on 30/03/16.
 */

class SimulatorController @Inject() (
    configuration: Configuration,
    scheduleEnvironment: ScheduleEnvironment)(implicit actorSystem: ActorSystem,
        mat: Materializer,
        ec: ExecutionContext) extends Controller {
  val logger = LoggerFactory.getLogger("SimulatorController")

  class ActorForward(queue: SourceQueue[JsValue]) extends Actor with ActorLogging {
    override def receive: Receive = {
      case x: SimulatedPosition =>
        queue.offer(Json.toJson(x))
    }
  }

  def peekMatValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
    val p = Promise[M]
    val s = src.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }
    (s, p.future)
  }

  def positionsBounded(minLat: Double,
    maxLat: Double,
    minLng: Double,
    maxLng: Double,
    cityTransit: Boolean,
    accelerationFactor: Double) = {
    positions(accelerationFactor, {
      (trip: Trip) =>
        val rawRoute = trip.route
        val ctCond = cityTransit || (((rawRoute.routeType != RouteType.BUS) || rawRoute.agencyId == AgencyId("000801")) && (rawRoute.routeType != RouteType.TRAM))
        ctCond && trip.stopTimes.exists({
          stopTime =>
            val lat = stopTime.stop.lat
            val lng = stopTime.stop.lng
            !(
              (lat < minLat)
              || (lat > maxLat)
              || (lng < minLng)
              || (lng > maxLng)
            )
        })

    })
  }

  def positionsAllNoCityTransport =
    positions(500,
      (trip: Trip) =>
        ((trip.route.routeType != RouteType.BUS) || trip.route.agencyId == AgencyId("000801"))
          && (trip.route.routeType != RouteType.TRAM)
    )

  def positions(accelerationFactor: Double, filterTrips: ((Trip) => Boolean)) = Action {
    val logging = Logging(actorSystem.eventStream, logger.getName)

    val (queueSource, futureQueue) = peekMatValue(Source.queue[JsValue](100000, OverflowStrategy.fail))

    futureQueue.map { queue =>
      val actorForward = actorSystem.actorOf(Props(new ActorForward(queue)))
      val ta = TimeAccelerator(System.currentTimeMillis(), 5 * 3600, accelerationFactor)
      val actualTrips = scheduleEnvironment.trips.filter(filterTrips)
      logger.info("launching scheduled trips")
      val actorSimulatedTrips = actorSystem.actorOf(Props(new ActorSimulatedTrips(actorForward, ta, actualTrips, scheduleEnvironment.date, accelerationFactor)))

      queue.watchCompletion().map { done =>
        actorForward ! PoisonPill
        actorSimulatedTrips ! StopSimulation
        println("Scheduler canceled")
      }
    }

    Ok.chunked(queueSource via EventSource.flow)
    // ref ! SimulatedPosition(3, 10.0, 20.0, TripId("tripid"), AgencyId("agencyId"), RouteShortName("rt"), SimulatedPositionStatus.MOVING, None)
  }
}
