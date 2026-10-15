package com.pianosteps.lessons

import kotlin.random.Random

/** Simple "grown-ups only" arithmetic question (after a press-and-hold in the UI). */
data class GateQuestion(val a: Int, val b: Int) {
    val text: String get() = "What is $a × $b?"
    val answer: Int get() = a * b
    fun check(input: String): Boolean = input.trim().toIntOrNull() == answer
}

object ParentGate {
    const val HOLD_MS = 1500L
    fun newQuestion(random: Random = Random.Default) = GateQuestion(random.nextInt(6, 10), random.nextInt(4, 10))
}
