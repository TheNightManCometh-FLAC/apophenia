package com.betterappmafia.apophenia.core.rhythm

/** An exact fraction, always reduced with a positive denominator. Durations are fractions of a whole note. */
class Rational private constructor(val num: Long, val den: Long) : Comparable<Rational> {

    operator fun plus(o: Rational) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Rational) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Rational) = of(num * o.num, den * o.den)
    operator fun times(n: Int) = of(num * n, den)
    operator fun div(o: Rational): Rational {
        require(o.num != 0L) { "division by zero" }
        return of(num * o.den, den * o.num)
    }
    operator fun div(n: Int) = of(num, den * n)
    operator fun unaryMinus() = of(-num, den)

    val isInteger: Boolean get() = den == 1L
    fun toDouble() = num.toDouble() / den

    override fun compareTo(other: Rational) = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Rational && num == other.num && den == other.den
    override fun hashCode() = 31 * num.hashCode() + den.hashCode()
    override fun toString() = if (den == 1L) "$num" else "$num/$den"

    companion object {
        val ZERO = Rational(0, 1)
        val ONE = Rational(1, 1)

        fun of(num: Long, den: Long = 1): Rational {
            require(den != 0L) { "zero denominator" }
            val g = gcd(Math.abs(num), Math.abs(den)).coerceAtLeast(1)
            val sign = if (den < 0) -1 else 1
            return Rational(sign * num / g, sign * den / g)
        }

        fun of(num: Int, den: Int = 1) = of(num.toLong(), den.toLong())

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }
}
