package com.example.usb_sector_rw

import CmdToPram
import FmFrecStruct
import FmPhasesStruct
import PHASES_BUF_SIZE
import SectorAnswer
import SectorCmd
import android.annotation.SuppressLint
import com.example.usb_sector_rw.losp.FALSE
import com.example.usb_sector_rw.msd.LospDev
import java.io.PrintWriter
import java.lang.Math.pow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

var answer = SectorAnswer()
var cycle_ok : ULong = 0u
var fix_count : ULong = 0u
private var handleOut: PrintWriter? = null
private var tickBegin: Long = 0
private var cycle = 0

var accuracy : Double = 0.0
var frec_current : Double = 0.0
var frec_old : Double = 0.0
var period_av : Double = 1.0
var frec_av : Double = 0.0
var frec_quad : Double = 0.0
var frec_min : Double = 9e9
var frec_max : Double = 0.0
var frec_rms : Double = 0.0
var m_sum : ULong = 0u
var cycle_av : ULong = 0u
var fix_count_exec : ULong = 0u
var tick_stop : ULong = 0u
var period_quad : Double = 0.0
var period_rms : Double = 0.0
var frec_acc : Double = 0.0
var period_acc : Double = 0.0
var period_acc_old : Double = 0.0

var is_no_set : Boolean = FALSE
var averaging_period : Float = 1f
var is_reset_set : Boolean = FALSE

var tracking_period_av1 : Double = 0.0
var tracking_period_av2 : Double = 0.0
var tracking_fix_count  : ULong  = 0u
var tracking_cycle_av1  : ULong  = 0u
var tracking_cycle_av2  : ULong  = 0u
var tracking_period_min : Double = 9.0
var tracking_period_max : Double = 0.0
var tracking_m_sum : Double = 0.0

var DOEZ_COEFF : Float = 1f

/**
 * Выполняет запрос частоты от микроконтроллера и выводит результат в лог или интерфейс.
 * Вывод в файл отключён — закомментирован.
 *
 * @param printfCount Счётчик количества вызовов (используется для выбора форматирования)
 * @return Обновлённый счётчик printf
 * @author Sergey Rundygin
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun frecUc(printfCount: Int, lospDev : LospDev, log: (String) -> Unit): Float {
    val cmd = SectorCmd()
    var freq: Float = 0f
    var p = FmFrecStruct.fromByteArray(ubyteArrayOf(0x0u))

    cmd.code = CmdToPram.PRAM_GET_FW_FREC.value
    cmd.sizeOut = FmFrecStruct.SIZE_BYTES.toUShort()

    if(!lospDev.lospExecCmd(cmd, log))
    {
        freq = -3.0f
    }
    else
    {
        if(!lospDev.getLospAnswer(cmd.code, answer, log))
        {
            freq = -4.0f
        }
        else
        {
            p = FmFrecStruct.fromByteArray(answer.dataOut)
            freq = p.frecAverage
        }
    }


    return freq
}

@OptIn(ExperimentalUnsignedTypes::class)
fun frec_test(lospDev: LospDev, log: (String) -> Unit): Boolean {
    var loss = false
    val cmd = SectorCmd()
    cmd.code = CmdToPram.PRAM_GET_FW_PHASES.value
    cmd.sizeOut = FmPhasesStruct.SIZE_BYTES.toUShort()

    if (lospDev.lospExecCmd(cmd, log)) {
        if (lospDev.getLospAnswer(cmd.code, answer, log)) {
            val p = FmPhasesStruct.fromByteArrayToFmPhasesStruct(answer.dataOut)
            val newFixCount = p.fixCount.toULong()
            val validSize = p.validSize.toULong()

            loss = (cycle_ok != 0UL || fix_count != 0UL) &&
                    (newFixCount > fix_count + validSize)

            if (loss) {
                cycle_ok = 0UL
                log("\n\rПотеряны зафиксированные фазы таймеров\n\r")
            } else {
                cycle_ok++
                log("Тест частотомера № $cycle_ok - Ok")
            }

            fix_count = newFixCount
        }
    }

    return !loss
}

@OptIn(ExperimentalUnsignedTypes::class)
fun frec_exec(lospDev : LospDev, log: (String) -> Unit, call_last : Boolean)
{
    if(is_reset_set)
    {
        is_reset_set = FALSE
        cycle_av  = 0UL;
        frec_min  = 9e9;
        frec_max  = 0.0;
        frec_quad = 0.0;
        period_quad = 0.0;
    }

    val cmd = SectorCmd()

    cmd.code = CmdToPram.PRAM_GET_FW_PHASES.value
    cmd.sizeOut = FmPhasesStruct.SIZE_BYTES.toUShort()

    lospDev.lospExecCmd(cmd, log)
    lospDev.getLospAnswer(cmd.code, answer, log)

    var p = FmPhasesStruct.fromByteArrayToFmPhasesStruct(answer.dataOut)

    if((p.validSize.toInt() != 0) && (fix_count_exec.toUInt() != p.fixCount))
    {
        var count = if (fix_count_exec == 0UL) {
            p.validSize.toInt()
        } else {
            minOf((p.fixCount - fix_count_exec).toInt(), p.maxSize.toInt())
        }

        var index : Int = p.validSize.toInt() - 0;
        fix_count_exec = p.fixCount.toULong();

        while ((count-- > 0) && (--index > 0))
        {
            var m : ULong = getULongFromByteArray(p.phases, index * 2) - getULongFromByteArray(p.phases, index * 2 - 2)
            var n : ULong = getULongFromByteArray(p.phases, index * 2 + 1) - getULongFromByteArray(p.phases, index * 2 - 1)

            if(n == 0uL)
            {
                log("\n\rНет сигнала на входе частотомера\n\r");
                cycle_av = 0u
                return
            }

            m_sum += m
            var fm_SystemCoreClock : Double = 16000000.0
            fm_SystemCoreClock = p.systemClock.toDouble();
            frec_current = (fm_SystemCoreClock * m.toDouble()) / n.toDouble();

            period_av = (period_av * cycle_av.toFloat() + 1 / frec_current) / (cycle_av.toFloat() + 1);
            frec_av   = 1 / period_av;

            frec_min = min(frec_min, frec_current)
            frec_max = max(frec_max, frec_current)
            frec_quad += frec_current * frec_current
            period_quad += (1.0 / frec_current).pow(2).toFloat()
            cycle_av++

            frec_rms = sqrt(1e-11f + frec_quad / cycle_av.toFloat() - frec_av.pow(2)).toDouble()
            frec_rms /= sqrt(1e-11f + cycle_av.toFloat()).toFloat()
            frec_acc = 100.0f * frec_rms / frec_av

            period_rms = sqrt(1e-11f + period_quad / cycle_av.toFloat() - period_av.pow(2)).toDouble()
            period_rms /= sqrt(1e-11f + cycle_av.toFloat()).toFloat()
            period_acc = 100.0f * period_rms / period_av

            if(tick_stop.toInt() == 0)
            {
                frec_old = frec_av;
//                FrequencyLogger.frecToFile(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0u, call_last, answer) TODO
                tick_stop = TimeUtils.tickEnd(averaging_period.toString());
            }

            if (call_last || ((accuracy < 1e-99)) && !TimeUtils.timeoutOk( tick_stop )
                || ((accuracy > 0) && (cycle_av > max (1.0, 1e3 / (accuracy * accuracy)).toUInt()) && (period_acc < accuracy)))
            {
                frec_old = frec_av;
                period_acc_old = period_acc;
//                FrequencyLogger.frecToFile(frec_current, frec_old, frec_min, frec_max, frec_rms, frec_acc, period_acc, m_sum, call_last, answer) TODO
                val seconds = 0.5 + max(0.01f, averaging_period)
                tick_stop += (1000000.0 * seconds).toULong()
                m_sum = 0u

                if (!is_no_set)
                {
                    cycle_av  = 0UL;
                    frec_min  = 9e9;
                    frec_max  = 0.0;
                    frec_quad = 0.0;
                    period_quad = 0.0;
                }
            }
        }
    }

    DOEZ_COEFF = p.dozeKoef

    if (period_acc_old.toInt() == 0)
    {
        log("Частота = $frec_old Гц")
    }
    else if(accuracy < 1e-99)
    {
        log("Частота = $frec_old +- ${frec_old * period_acc_old / 1e2} Гц")
    }
    else
    {
        log("Частота = $frec_old +- $period_acc_old Гц")
    }
}

//@SuppressLint("DefaultLocale")
//@OptIn(ExperimentalUnsignedTypes::class)
//fun frec_tracking(lospDev : LospDev, log: (String) -> Unit)
//{
//    var output_log : String = ""
//
//    if(!frec_test(lospDev, log))
//    {
//        tracking_m_sum = 0.0
//        tracking_cycle_av2 = 0u
//        tracking_cycle_av1 = 0u
//    }
//
//    var p = FmPhasesStruct.fromByteArrayToFmPhasesStruct(answer.dataOut)
//
//    if((p.fixCount > 1u) && (tracking_fix_count.toUInt() != p.fixCount))
//    {
//        var count = if (tracking_fix_count == 0UL) {
//            p.validSize.toInt()
//        } else {
//            minOf((p.fixCount - tracking_fix_count).toInt(), p.maxSize.toInt())
//        }
//
//        var index : Int = p.validSize.toInt() - 0;
//        tracking_fix_count = p.fixCount.toULong();
//
//        while ((count-- > 0) && (--index > 0))
//        {
//            var m : ULong = getULongFromByteArray(p.phases, index * 2) - getULongFromByteArray(p.phases, index * 2 - 2)
//            var n : ULong = getULongFromByteArray(p.phases, index * 2 + 1) - getULongFromByteArray(p.phases, index * 2 - 1)
//
//            if(n == 0uL)
//            {
//                log("\n\rНет сигнала на входе частотомера\n\r");
//                return
//            }
//
//            tracking_m_sum += m.toFloat()
//            var period : Float = n.toFloat() / (p.systemClock.toFloat() * m.toFloat())
//            tracking_period_av1 = (tracking_period_av1 * tracking_cycle_av1.toFloat() + period) / (tracking_cycle_av1.toFloat() + 1f)
//            tracking_cycle_av1 = minOf(tracking_cycle_av1 + 1UL, 40000UL)
//            tracking_period_av2 = (tracking_period_av2 * tracking_cycle_av2.toFloat() + period) / (tracking_cycle_av2.toFloat() + 1f)
//            tracking_cycle_av2 = minOf(tracking_cycle_av2 + 1UL,
//                tracking_cycle_av1.toDouble().pow(0.3).toULong()
//            )
//
//            if(tracking_cycle_av2 > 2UL)
//            {
//                tracking_period_min = min( tracking_period_min, tracking_period_av2 / tracking_period_av1 );
//                tracking_period_max = max( tracking_period_max, tracking_period_av2 / tracking_period_av1 );
//            }
//
//            if ((tracking_cycle_av2 > 1UL) && ((tracking_period_av2 > tracking_period_av1 * 3.9) || (tracking_period_av2 < tracking_period_av1 * 0.23)))
//            {
////                output_log = (String.format("\n\rСигнал %s = %.2f Гц\n\r", if (period < tracking_period_av1) "вырос" else "упал", 1 / tracking_period_av2))
//                output_log = (String.format("Сигнал %s = %.2f Гц", if (period < tracking_period_av1) "вырос" else "упал", 1 / tracking_period_av2))
//                tracking_m_sum = 0.0
//                tracking_cycle_av2 = 0UL
//                tracking_cycle_av1 = 0UL
//                tracking_period_min = 9.0
//                tracking_period_max = 0.0
//
//            }
//        }
//    }
//
//    DOEZ_COEFF = p.dozeKoef
//
//    log(
//        String.format( output_log +
//            "Частота = %.2f ± %.2f Гц    \b\b\b",
//            1 / tracking_period_av1,
//            (1 / sqrt(tracking_m_sum)) / tracking_period_av1
//        )
//    )
//}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalUnsignedTypes::class)
fun frec_tracking(lospDev: LospDev, log: (String) -> Unit) {
    var output_log = ""

    log(">>> Запуск frec_tracking")

    if (!frec_test(lospDev, log)) {
        log("frec_test() вернул false — сбрасываем tracking-состояние")
        tracking_m_sum = 0.0
        tracking_cycle_av2 = 0u
        tracking_cycle_av1 = 0u
    } else {
        log("frec_test() прошёл успешно")
    }

    val p = FmPhasesStruct.fromByteArrayToFmPhasesStruct(answer.dataOut)
    log("Получен FmPhasesStruct: fixCount=${p.fixCount}, validSize=${p.validSize}, maxSize=${p.maxSize}, systemClock=${p.systemClock}, dozeKoef=${p.dozeKoef}")

    if ((p.fixCount > 1u) && (tracking_fix_count.toUInt() != p.fixCount)) {
        var count = if (tracking_fix_count == 0UL) {
            p.validSize.toInt().also { log("Начальный запуск — count=$it") }
        } else {
            val delta = (p.fixCount - tracking_fix_count).toInt()
            minOf(delta, p.maxSize.toInt()).also {
                log("Обновление фаз — delta=$delta, count=$it")
            }
        }

        var index = p.validSize.toInt()
        tracking_fix_count = p.fixCount.toULong()
        log("Обновляем tracking_fix_count → $tracking_fix_count")

        while ((count-- > 0) && (--index > 0)) {
            log("Итерация count=$count, index=$index")

            val m = getULongFromByteArray(p.phases, index * 2) - getULongFromByteArray(p.phases, index * 2 - 2)
            val n = getULongFromByteArray(p.phases, index * 2 + 1) - getULongFromByteArray(p.phases, index * 2 - 1)

            log("m=$m, n=$n")

            if (n == 0uL) {
                log("Нет сигнала на входе частотомера — n == 0")
                return
            }

            val systemClock = p.systemClock.toFloat()
            val period = n.toFloat() / (systemClock * m.toFloat())
            log("Расчёт: period=$period, systemClock=$systemClock")

            tracking_m_sum += m.toFloat()
            tracking_period_av1 = (tracking_period_av1 * tracking_cycle_av1.toFloat() + period) / (tracking_cycle_av1.toFloat() + 1f)
            tracking_cycle_av1 = minOf(tracking_cycle_av1 + 1UL, 40000UL)
            tracking_period_av2 = (tracking_period_av2 * tracking_cycle_av2.toFloat() + period) / (tracking_cycle_av2.toFloat() + 1f)
            tracking_cycle_av2 = minOf(tracking_cycle_av2 + 1UL, tracking_cycle_av1.toDouble().pow(0.3).toULong())

            log("Обновлено: tracking_m_sum=$tracking_m_sum, tracking_period_av1=$tracking_period_av1, tracking_period_av2=$tracking_period_av2")
            log("Циклы: tracking_cycle_av1=$tracking_cycle_av1, tracking_cycle_av2=$tracking_cycle_av2")

            if (tracking_cycle_av2 > 2UL) {
                val ratio = tracking_period_av2 / tracking_period_av1
                tracking_period_min = min(tracking_period_min, ratio)
                tracking_period_max = max(tracking_period_max, ratio)
                log("Отношение av2/av1=$ratio, min=$tracking_period_min, max=$tracking_period_max")
            }

            if ((tracking_cycle_av2 > 1UL) &&
                ((tracking_period_av2 > tracking_period_av1 * 3.9) || (tracking_period_av2 < tracking_period_av1 * 0.23))) {

                val trend = if (period < tracking_period_av1) "вырос" else "упал"
                val freq = 1 / tracking_period_av2
                output_log = String.format("Сигнал %s = %.2f Гц", trend, freq)

                log("Порог изменения превышен: $output_log")

                tracking_m_sum = 0.0
                tracking_cycle_av2 = 0UL
                tracking_cycle_av1 = 0UL
                tracking_period_min = 9.0
                tracking_period_max = 0.0
            }
        }
    } else {
        log("Нет изменений или недостаточно данных: fixCount=${p.fixCount}, tracking_fix_count=$tracking_fix_count")
    }

    DOEZ_COEFF = p.dozeKoef
    log("Обновлён DOEZ_COEFF = $DOEZ_COEFF")

    val freqAverage = 1 / tracking_period_av1
    val accuracy = (1 / sqrt(tracking_m_sum)) / tracking_period_av1
    val finalLog = String.format("%s\nЧастота = %.2f ± %.2f Гц", output_log, freqAverage, accuracy)
    log(finalLog)
}

fun getULongFromByteArray(bytes: ByteArray, index: Int): ULong {
    val offset = index * 8

    return ((bytes[offset + 7].toULong() and 0xFFu) shl 56) or
            ((bytes[offset + 6].toULong() and 0xFFu) shl 48) or
            ((bytes[offset + 5].toULong() and 0xFFu) shl 40) or
            ((bytes[offset + 4].toULong() and 0xFFu) shl 32) or
            ((bytes[offset + 3].toULong() and 0xFFu) shl 24) or
            ((bytes[offset + 2].toULong() and 0xFFu) shl 16) or
            ((bytes[offset + 1].toULong() and 0xFFu) shl 8)  or
            ((bytes[offset + 0].toULong() and 0xFFu))
}