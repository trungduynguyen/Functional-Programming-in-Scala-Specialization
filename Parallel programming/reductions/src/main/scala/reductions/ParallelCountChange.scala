package reductions

import org.scalameter._
import common._

object ParallelCountChangeRunner {

  @volatile var seqResult = 0

  @volatile var parResult = 0

  val standardConfig = config(
    Key.exec.minWarmupRuns -> 20,
    Key.exec.maxWarmupRuns -> 40,
    Key.exec.benchRuns -> 80,
    Key.verbose -> true
  ) withWarmer(new Warmer.Default)

  def main(args: Array[String]): Unit = {
    val amount = 250
    val coins = List(1, 2, 5, 10, 20, 50)
    val seqtime = standardConfig measure {
      seqResult = ParallelCountChange.countChange(amount, coins)
    }
    println(s"sequential result = $seqResult")
    println(s"sequential count time: $seqtime ms")

    def measureParallelCountChange(threshold: ParallelCountChange.Threshold): Unit = {
      val fjtime = standardConfig measure {
        parResult = ParallelCountChange.parCountChange(amount, coins, threshold)
      }
      println(s"parallel result = $parResult")
      println(s"parallel count time: $fjtime ms")
      println(s"speedup: ${seqtime / fjtime}")
    }

    measureParallelCountChange(ParallelCountChange.moneyThreshold(amount))
    measureParallelCountChange(ParallelCountChange.totalCoinsThreshold(coins.length))
    measureParallelCountChange(ParallelCountChange.combinedThreshold(amount, coins))
  }
}

object ParallelCountChange {

  /** Returns the number of ways change can be made from the specified list of
    *  coins for the specified amount of money.
    */
  def countChange(money: Int, coins: List[Int]): Int = {
    def loop(money: Int, coins: List[Int], acc: Int = 0): Int = {
      money match {
        case 0 => acc + 1
        case x if x < 0 => acc
        case _ => loop(money - coins.head, coins, acc + countChange(money, coins.tail))
      }
    }
    if (money == 0) 1
    else if (money < 0 || coins.isEmpty) 0
    else loop(money, coins)
  }

  type Threshold = (Int, List[Int]) => Boolean

  /** In parallel, counts the number of ways change can be made from the
    *  specified list of coins for the specified amount of money.
    */
  def parCountChange(money: Int, coins: List[Int], threshold: Threshold): Int = {
    // run sequential algorithm if threshold return true
    if (money <= 0 || coins.isEmpty || threshold(money, coins)) {
      countChange(money, coins)
    } else {
      val (a, b) = parallel(parCountChange(money - coins.head, coins, threshold), parCountChange(money, coins.tail, threshold))
      a + b
    }
  }

  /** Threshold heuristic based on the starting money. */
  def moneyThreshold(startingMoney: Int): Threshold = {
    // returns true when the amount of money is less than or equal to 2 / 3 of the starting amount
    (money: Int, coins: List[Int]) => money <= startingMoney * 2 / 3
  }

  /** Threshold heuristic based on the total number of initial coins. */
  def totalCoinsThreshold(totalCoins: Int): Threshold = {
    // returns true when the number of coins is less than or equal to the 2 / 3 of the initial number of coins
    (money: Int, coins: List[Int]) => coins.length <= totalCoins * 2 / 3
  }


  /** Threshold heuristic based on the starting money and the initial list of coins. */
  def combinedThreshold(startingMoney: Int, allCoins: List[Int]): Threshold = {
    // returns true when
    // the amount of money multiplied with the number of remaining coins is less than or equal to
    // the starting money multiplied with the initial number of coins divided by 2
    (money: Int, coins: List[Int]) => money * coins.length <= startingMoney * allCoins.length / 2
  }
}