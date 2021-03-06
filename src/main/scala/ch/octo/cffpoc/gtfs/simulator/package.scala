package ch.octo.cffpoc.gtfs

import ch.octo.cffpoc.gtfs.RouteType.RouteType
import org.joda.time.DateTime

/**
 * Created by alex on 06.10.16.
 */
package object simulator {

  object SimulatedPositionStatus extends Enumeration {
    val START, MOVING, END = Value
  }

  case class SimulatedPosition(
      secondsOfDay: Int,
      lat: Double,
      lng: Double,
      tripId: TripId,
      agencyId: AgencyId,
      routeShortName: RouteShortName,
      routeLongName: RouteLongName,
      routeType: RouteType,
      status: SimulatedPositionStatus.Value,
      stopId: Option[StopId]) {

    def withSecondOfDay(newSecondOfDay: Int): SimulatedPosition = {
      SimulatedPosition(
        newSecondOfDay,
        lat,
        lng,
        tripId,
        agencyId,
        routeShortName,
        routeLongName,
        routeType,
        status,
        stopId
      )
    }

    override def toString: String = f"""${secondsOfDay / 3600}%02d:${(secondsOfDay / 60) % 60}%02d:${secondsOfDay % 60}%02d\t$lat%2.2f\t$lng%2.2f\t${routeShortName.value}\t$status\t${tripId.value}\t${stopId.getOrElse("-")}"""
  }

}
