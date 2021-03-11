package kinesis.mock.models

import io.circe._

final case class Shard(
    adjacentParentShardId: Option[String],
    hashKeyRange: HashKeyRange,
    parentShardId: Option[String],
    sequenceNumberRange: SequenceNumberRange,
    shardId: String
) {
  val numericShardId: Int = shardId.takeRight(12).toInt
  val isOpen: Boolean = sequenceNumberRange.endingSequenceNumber.isEmpty
}

object Shard {
  def shardId(shardIndex: Int): String =
    "shardId-" + s"00000000000$shardIndex".takeRight(12)
  implicit val shardOrdering: Ordering[Shard] = new Ordering[Shard] {
    override def compare(x: Shard, y: Shard): Int =
      x.numericShardId.compare(y.numericShardId)
  }

  implicit val shardCirceEncoder: Encoder[Shard] = Encoder.forProduct5(
    "AdjacentParentShardId",
    "HashKeyRange",
    "ParentShardId",
    "SequenceNumberRange",
    "ShardId"
  )(x =>
    (
      x.adjacentParentShardId,
      x.hashKeyRange,
      x.parentShardId,
      x.sequenceNumberRange,
      x.shardId
    )
  )

  implicit val shardCirceDecoder: Decoder[Shard] = { x =>
    for {
      adjacentParentShardId <- x
        .downField("AdjacentParentShardId")
        .as[Option[String]]
      hashKeyRange <- x.downField("HashKeyRange").as[HashKeyRange]
      parentShardId <- x.downField("ParentShardId").as[Option[String]]
      sequenceNumberRange <- x
        .downField("SequenceNumberRange")
        .as[SequenceNumberRange]
      shardId <- x.downField("ShardId").as[String]
    } yield Shard(
      adjacentParentShardId,
      hashKeyRange,
      parentShardId,
      sequenceNumberRange,
      shardId
    )

  }
}