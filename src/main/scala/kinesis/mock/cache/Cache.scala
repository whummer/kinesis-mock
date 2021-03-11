package kinesis.mock
package cache

import cats.effect._
import cats.effect.concurrent.Ref
import cats.syntax.all._

import kinesis.mock.api._
import kinesis.mock.models._

class Cache private (
    ref: Ref[IO, Streams],
    semaphores: CacheSemaphores,
    config: CacheConfig
) {

  def addTagsToStream(
      req: AddTagsToStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquire.ifM(
      ref.get.flatMap(streams =>
        req.addTagsToStream(streams).traverse(ref.set)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for AddTagsToStream"
          )
        )
      )
    )

  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.addTagsToStream.tryAcquire.ifM(
      ref.get.flatMap(streams =>
        req
          .removeTagsFromStream(streams)
          .traverse(ref.set)
      ),
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for RemoveTagsFromStream"
          )
        )
      )
    )

  def createStream(
      req: CreateStreamRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.createStream.tryAcquire.ifM(
      for {
        streams <- ref.get
        res <- req
          .createStream(
            streams,
            config.shardLimit,
            config.awsRegion,
            config.awsAccountId
          )
          .traverse(updated =>
            (IO.sleep(config.createStreamDuration) *>
              // Update the stream as ACTIVE after a small, configured delay
              IO(
                updated.findAndUpdateStream(req.streamName)(x =>
                  x.copy(status = StreamStatus.ACTIVE)
                )
              )).start.void
          )
      } yield res,
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for CreateStream"
          )
        )
      )
    )

  def deleteStream(
      req: DeleteStreamRequest
  )(implicit
      T: Timer[IO],
      CS: ContextShift[IO]
  ): IO[Either[KinesisMockException, Unit]] =
    semaphores.deleteStream.tryAcquire.ifM(
      for {
        streams <- ref.get
        res <- req
          .deleteStream(streams)
          .traverse(updated =>
            (IO.sleep(config.deleteStreamDuration) *>
              // Remove the stream after a small, configured delay
              IO(
                updated.removeStream(req.streamName)
              )).start.void
          )
      } yield res,
      IO.pure(
        Left(
          LimitExceededException(
            "Rate limit exceeded for CreateStream"
          )
        )
      )
    )
}

object Cache {
  def apply(config: CacheConfig)(implicit C: Concurrent[IO]): IO[Cache] = for {
    ref <- Ref.of[IO, Streams](Streams.empty)
    semaphores <- CacheSemaphores.create
  } yield new Cache(ref, semaphores, config)
}
