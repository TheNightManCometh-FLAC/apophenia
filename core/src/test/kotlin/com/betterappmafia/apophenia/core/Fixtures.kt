package com.betterappmafia.apophenia.core

import com.betterappmafia.apophenia.core.degree.DegreeToken
import com.betterappmafia.apophenia.core.pitch.Letter
import com.betterappmafia.apophenia.core.pitch.PitchName

/** The 35 roots: 7 letters x 5 accidentals (bb b n # x). */
val ALL_35_ROOTS: List<PitchName> = Letter.entries.flatMap { l -> (-2..2).map { PitchName(l, it) } }

fun names(s: String): List<PitchName> = s.trim().split(' ').filter(String::isNotEmpty).map(PitchName::parse)

fun degrees(s: String): List<DegreeToken> = DegreeToken.list(s)
