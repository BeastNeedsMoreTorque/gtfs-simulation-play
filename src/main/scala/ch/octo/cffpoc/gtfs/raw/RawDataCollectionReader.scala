package ch.octo.cffpoc.gtfs.raw

import scala.io.Source

/**
 * Created by alex on 03/05/16.
 */
trait RawDataCollectionReader[T] {

  def builReadFunction(header: Array[String]): (Array[String]) => T

  def load(filename: String): Iterator[T] = {
    val itLines = Source.fromFile(filename)
      .getLines()
    val header = RawDataCollectionReader.splitLine(itLines.next())
    val fRead = builReadFunction(header)

    itLines
      .filter(_.trim != "")
      .map(l => fRead(RawDataCollectionReader.splitLine(l)))
  }
}

object RawDataCollectionReader {
  val reCommaOut = """,(?=([^\"]*\"[^\"]*\")*[^\"]*$)""".r
  val reQuote = """^"(.*)"$""".r
  def splitLine(line: String): Array[String] = {
    reCommaOut.split(line).map(_ match {
      case reQuote(x) => x
      case x => x
    })
  }

}
