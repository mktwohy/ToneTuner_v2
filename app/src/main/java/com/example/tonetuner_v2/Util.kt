package com.example.tonetuner_v2

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.signallib.enums.Note
import com.example.signallib.enums.Note.Companion.minus
import com.example.signallib.enums.Note.Companion.plus
import com.example.tonetuner_v2.app.AppModel
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.BlockingQueue
import kotlin.math.*
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

fun List<List<Harmonic>>.averageLists() =
    this
        .flatten()
        .groupBy { it.freq }
        .map { (freq, harmonics) ->
            Harmonic(freq, harmonics.map { it.mag }.average().toFloat())
        }

//fun List<Harmonic>.normalizeBySum(): List<Harmonic> {
//    val magSum = this.map { it.mag }.sum()
//    return this.onEach { it.mag /= magSum }
//}



fun List<Harmonic>.assignMagsToIndices(size: Int): List<Float>{
    if (this.isEmpty()) return listOf()

    val ret = FloatArray(size)

    val indexToValue = this.map { it.freq.toInt() to it.mag }
//    val size = indexToValue.maxOf { it.first } + 1

    for ((index, value) in indexToValue){
        if (index < size)
            ret[index] = value
    }
    return ret.toList()
}

fun Int.fact() =
    (1..this).reduce { acc, i -> acc * i }

fun combinations(n: Int, k: Int) =
    n.fact() / k.fact() * (n - k).fact()

fun getAllOrderedPairs(l1: List<Any>, l2: List<Any>): List<Pair<Any, Any>>{
    val pairs = mutableListOf<Pair<Any, Any>>()
    for (i in l1){
        for (j in l2){
            pairs.add(i to j)
        }
    }
    return pairs
}

fun List<Harmonic>.toFingerPrint(): List<Float> {
    val f = this.map { it.freq.toInt() to it.mag.toFloat() }.toMap()
    return List(AppModel.FINGERPRINT_SIZE){ i -> f[i] ?: 0f }
}

fun ClosedRange<Float>.toList(step: Float): List<Float>{
    val df = DecimalFormat("#.#")
    df.roundingMode = RoundingMode.HALF_DOWN
    val size = ((endInclusive - start) / step).roundToInt() + 1
    return List(size) { index ->
        (step * index + start).also { df.format(it) }
    }
}

fun Float.toRadian() = this * Math.PI.toFloat() / 180

fun Float.toDegree() = this * 180 / Math.PI.toFloat()

fun List<Float>.normalizeBySum(): List<Float> {
    val sum = this.sum()
    return this.map { it / sum }
}

fun List<Harmonic>.toGraphRepr() =
    this.asSequence()
        .onEach { it.freq = freqToPitch(it.freq) } // convert frequencies to pitch
        .map { it.freq.toInt() to it.mag }          // convert pitches to Int indices
        .groupBy { it.first }                       // group by index
        .filter { it.key >= 0 }                      // filter positive indices
        .map { (index, harmonics) ->                // map so output is List<index to max freq>
            index to harmonics.maxOf { it.second }
        }
        .toMap()
        .let { indexToValue ->
            val maxIndex = indexToValue.maxOfOrNull { it.key } ?: 0
            MutableList(maxIndex + 1){ indexToValue[it] ?: 0f }
        }
        .normalizeBySum()


/** from https://psychology.wikia.org/wiki/Pitch_perception */
fun freqToPitch(freq: Float) = 69 + 12 * log(freq/440f, 2f)

/**
 * like toString(), but it returns a substring a desired length. The end is padded with
 * zeros if needed.
 */
fun Float.toString(length: Int) =
    this.toString().padEnd(length, '0').substring(0, length)

/** Similar to offer(), but, if there is no space, it removes an element to make room */
fun <T> BlockingQueue<T>.forcedOffer(element: T){
    if(remainingCapacity() == 0) poll()
    offer(element)
}

fun <T> BlockingQueue<T>.clearAndOffer(element: T){
    clear()
    offer(element)
}

fun List<List<Harmonic>>.sumLists(): List<Harmonic> =
    when(size){
        0 -> listOf()
        1 -> this[0]
        else -> this
            .flatten()
            .groupBy { it.freq }
            .map { group -> Harmonic(
                    group.key,
                    group.value.map { it.mag }.sum()
                )
            }
    }

fun <T> List<List<T>>.elementsAtIndex(index: Int): List<T> {
    val elements = mutableListOf<T>()
    for(list in this){
        if (index in list.indices) elements += list[index]
    }
    return elements
}

fun <T> List<List<T>>.groupByIndex() =
    if (this.isEmpty())
        listOf()
    else
        List(this.maxOf { it.size } ){ this.elementsAtIndex(it) }

@JvmName("sumListsFloat")
fun List<List<Float>>.sumLists(): List<Float> =
    this.groupByIndex().map { it.sum() }

operator fun Color.plus(that: Color) =
    Color(
        this.red/2 + that.red/2,
        this.green/2 + that.green/2,
        this.blue/2 + that.blue/2,
        this.alpha/2 + that.alpha/2
    )

/** Converts frequency to closest note estimate*/
fun Float.toNote(): Note?{
    // check if frequency is out of bounds
    if(this < Note.C_0.freq || this > Note.B_8.freq) return null

    // find the upper estimate for the note
    var upperEst = Note.Cs0
    while(upperEst.freq < this && upperEst != Note.B_8){
        upperEst += 1
    }

    // get the lower estimate for the note
    val lowerEst = upperEst - 1

    val upperErr = abs(upperEst.freq - this)
    val lowerErr = abs(lowerEst.freq - this)

    return if(upperErr < lowerErr) upperEst else lowerEst
}

/** Converts frequency to the closest note and its error (cents) */
fun Float.toNoteAndCents(): Pair<Note?, Int>{
    val note = this.toNote() ?: return Pair(null, 0)
    val hzError = this - note.freq

    val centsError =
        if(hzError > 0){
            val hzToNextNote = (note + 1).freq - note.freq
            (100 * hzError/hzToNextNote).toInt()
        } else{
            if(note == Note.C_0){ 0 }
            else{
                val hzToPrevNote =  note.freq - (note - 1).freq
                (100 * hzError/hzToPrevNote).toInt()
            }

        }
    return Pair(note, centsError)
}

fun logd(message: Any){ Log.d("m_tag",message.toString()) }

fun logTime(title: String = "", block: () -> Unit){
    measureTimeMillis { block() }.also { logd("$title $it ms") }
}

fun avgTimeMillis(repeat: Int, block: () -> Unit): Float {
    val times = mutableListOf<Long>()
    repeat(repeat){
        measureTimeMillis{ block() }
            .also{ times += it }
    }
    return times.average().toFloat()
}

fun avgTimeNano(repeat: Int, block: () -> Any?): Float {
    val times = mutableListOf<Long>()
    repeat(repeat){
        measureNanoTime{ block() }
            .also{ times += it }
    }
    return times.average().toFloat()
}

// todo deconstruction and if else assignment
fun arangeOLD(start: Float, stop: Float? = null, step: Float = 1f): List<Float> {
    val lStart: Float
    val lStop: Float

    if (stop == null) {
        lStart = 0f
        lStop = start-1f
    }
    else {
        lStart = start
        lStop = stop
    }

    val size = ((lStop-lStart)/step).roundToInt() + 1
    return List(size) { index -> step*index + lStart }
}

fun arange(start: Float, stop: Float? = null, step: Float = 1f): List<Float> {
    val (lStart, lStop) =
        if (stop == null)
            0f to start - 1f
        else
            start to stop

    val size = ((lStop-lStart)/step).roundToInt() + 1
    return List(size) { index -> step*index + lStart }
}

data class Harmonic(var freq: Float, var mag: Float)

fun poly(x: List<Float>, y: List<Float>): Harmonic {
    val coef = polyFit(x,y)
    val a = coef[0]
    val b = coef[1]
    val c = coef[2]

    return Harmonic(-b/(2*a), c-b.pow(2)/(4*a))
}

fun quadInterp(x: Float, xVals: List<Float>, yVals: List<Float>): Float {
    val coef = polyFit(xVals, yVals)
    return coef[0] * x.pow(2) + coef[1] * x + coef[2]
}

fun polyFit(x: List<Float>, y: List<Float> ) : List<Float> {
    val denom = (x[0] - x[1])*(x[0] - x[2])*(x[1] - x[2])
    val a = (x[2] * (y[1] - y[0]) + x[1] * (y[0] - y[2]) + x[0] * (y[2] - y[1])) / denom
    val b = (x[2].pow(2) * (y[0] - y[1]) + x[1].pow(2) * (y[2] - y[0]) + x[0].pow(2) * (y[1] - y[2])) / denom
    val c = (x[1]*x[2]*(x[1]-x[2])*y[0]+x[2] * x[0] * (x[2] - x[0]) * y[1] + x[0] * x[1] * (x[0] - x[1]) * y[2]) / denom

    return listOf(a,b,c)
}

fun List<Float>.normalize(
    lowerBound: Float = -1f,
    upperBound: Float = 1f
) = this.toMutableList().apply { normalize(lowerBound, upperBound) }

fun MutableList<Float>.normalize(
    lowerBound: Float = -1f,
    upperBound: Float = 1f
) {
    //Check that array isn't empty
    if (isEmpty()) return

    val minValue   = this.minByOrNull { it }!!
    val maxValue   = this.maxByOrNull { it }!!
    val valueRange = (maxValue - minValue).toFloat()
    val boundRange = (upperBound - lowerBound).toFloat()

    //Check that array isn't already normalized
    // (I would use in range, but this produces excess memory)
    if ((minValue == 0f && maxValue == 0f)
        || (maxValue <= upperBound && maxValue > upperBound
                && minValue >= lowerBound && minValue < lowerBound)) {
        return
    }

    //Normalize
    for (i in indices) {
        this[i] = ((boundRange * (this[i] - minValue)) / valueRange) + lowerBound
    }
}
