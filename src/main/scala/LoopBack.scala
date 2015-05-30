import Chisel._
import Uart._

class LoopBack(val wtime: Int, entries: Int, modifier: (UInt) => UInt) extends Module {
  val io = new Bundle {
    val tx = Bool(OUTPUT)
    val rx = Bool(INPUT)
  }

  val uart = Module(new BufferedUart(wtime, entries))

  io.tx <> uart.io.txd
  io.rx <> uart.io.rxd

  uart.io.deq.ready := Bool(false)
  uart.io.enq.valid := Bool(false)
  uart.io.enq.bits  := UInt(0)

  when (uart.io.enq.ready) {
    uart.io.deq.ready := Bool(true)
  }

  when (uart.io.deq.valid) {
    uart.io.enq.valid := Bool(false)
    uart.io.enq.bits  := modifier(uart.io.deq.bits)
  }
}

object LoopBack {

  def main(args: Array[String]): Unit = {
    val wtime = 0x1adb
    val entries = 128

    chiselMainTest(args, () => Module(new LoopBack(wtime, entries, x => x))) { c =>
      new LoopBackTest(c)
    }
  }

  class LoopBackTestModule(c: LoopBack) extends Module {
    val io = new Bundle {
      val tx = Decoupled(UInt(width = 8)).flip
      val rx = Valid(UInt(width = 8))
    }
    val codec = Module(new Uart(c.wtime))

    codec.io.txd <> c.io.rx
    codec.io.rxd <> c.io.tx

    io.tx <> codec.io.enq
    io.rx <> codec.io.deq
  }

  class LoopBackTest(c: LoopBack) extends Tester(c) {
    val tester = Module(new LoopBackTestModule(c))

    poke(tester.io.tx.valid, 0)

    step(10)

    poke(tester.io.tx.valid, 1)
    poke(tester.io.tx.bits, 0xAA)

    do {
      step(1)
    } while (peek(tester.io.rx.valid) == 0)

    expect(tester.io.rx.valid, 1)
    expect(tester.io.rx.bits, 0xAA)
  }
}
