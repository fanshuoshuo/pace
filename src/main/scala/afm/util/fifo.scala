package afm.util

import collection.JavaConversions._
import java.util.concurrent.{ LinkedBlockingQueue, BlockingQueue, TimeUnit }

class FIFOStream[A](val queue: BlockingQueue[Option[A]] = new LinkedBlockingQueue[Option[A]]()) {
  lazy val toStream: Stream[A] = queue2stream
  private def queue2stream: Stream[A] = queue take match {
    case Some(a) => Stream cons (a, queue2stream)
    case None => Stream empty
  }
  def close() = queue add None
  def enqueue(a: A) = queue.offer(Some(a), 1, TimeUnit.HOURS)
}

class FIFO[A](val queue: BlockingQueue[Option[A]] = new LinkedBlockingQueue[Option[A]]()) {

  class BlockingIterator extends Iterator[A] {
    var nextVal: Option[A] = None;

    def hasNext = {
      nextVal = queue.take
      nextVal match {
        case Some(_) => true
        case None => false
      }
    }

    def next = nextVal.get
  }

  def toIterator = new BlockingIterator

  def close() = queue add None
  def enqueue(a: A) = queue.offer(Some(a), 1, TimeUnit.HOURS)
}
