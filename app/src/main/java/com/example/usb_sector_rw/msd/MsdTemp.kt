package com.example.usb_sector_rw.msd

import SectorAnswer
import SectorCmd
import com.example.usb_sector_rw.answer
import java.lang.Math.pow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

var answer_temp = SectorAnswer()
var cycle_ok_temp : ULong = 0UL
var fix_count_temp : ULong = 0UL
//$ Температура абсолютного нуля в °C
val TEMP_ABS : Float  = -273.15f; // -273.15  26.5  0
//$ Заданная точность усреднения температуры МК в процентах
var accuracy_temp : Float = 0f
//$ Достигнутая точность усреднения температуры МК в относительных единицах
var temp_current : Float = 0f; //$< Текущая измеренная температура МК в °C
var old_current_temp : Float  = 0f; //$< Последняя в предыдущей выборке измеренная температура МК в °C
var temp_old : Float     = 0f; //$< Предыдущая усредненная температура МК в °C
var temp_av : Float      = 0f; //$< Усредняемая температура МК в °C
var temp_quad : Float    = 0f; //$< Сумма квадратов измеренных температур МК в Кельвинах
var temp_min : Float     = 999f; //$< Минимальная температура МК при усреднении в °C
var temp_max : Float     = -199f; //$< Максимальная температура МК при усреднении в °C
var temp_rms : Float     = 0f; //$< Среднеквадратичное отклонение при усреднении в °C
var cycle_av_temp : UInt     = 0u; //$< Число проведенных усреднений температуры МК
//	static u32 fix_count    = 0; //$< Обработанное число измерений температуры МК
var exec_fix_count_temp : UInt   = 0u; //$< Обработанное число измерений температуры МК (u8,u16 для проверки переполнения TempFixCount)
var exec_tick_stop_temp : ULong   = 0u; //$< Время завершения усреднений в формате UTC
var sleep_count : UInt  = 0u; //$< Число пропусков между запросами данных

fun get_temp(lospDev : LospDev, log: (String) -> Unit)
{
    val cmd = SectorCmd()
    cmd.code = CmdToPram.PRAM_GET_TEMP.value
    cmd.sizeOut = PramTempStruct.SIZE_IN_BYTES.toUShort()

    lospDev.lospExecCmd(cmd, log)
    lospDev.getLospAnswer(cmd.code, answer_temp, log)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun temp_test(lospDev : LospDev, log: (String) -> Unit) : Boolean
{
    var loss : Boolean = true
    get_temp(lospDev, log)

    var p_temp = PramTempStruct.fromByteArray(answer_temp.dataOut)

    loss = ((0UL != cycle_ok_temp++) || (fix_count_temp != 0UL)) && (p_temp.tempFixCount > fix_count_temp + p_temp.validSize);
    fix_count_temp = p_temp.tempFixCount.toULong();

    if (loss)
        log("\n\rПотеряны результаты измерений температуры МК\n\r")
    else
        log("Tест измерений температуры МК № $cycle_ok_temp - Ok")
    return loss;
}

@OptIn(ExperimentalUnsignedTypes::class)
fun temp_exec(lospDev : LospDev, log: (String) -> Unit, call_last : Boolean, is_graph : Boolean) : Float
{
    get_temp(lospDev, log)
    // sleep TODO

    var p_temp = PramTempStruct.fromByteArray(answer_temp.dataOut)

//    var i = 0
//    for(b in p_temp.buf)
//    {
//        log("buf[$i] = ${"%02X".format(p_temp.buf[i].toInt())}")
//        i++
//    }

    if (exec_fix_count_temp != p_temp.tempFixCount)
    {
        if ((exec_tick_stop_temp != 0UL) && (exec_fix_count_temp + p_temp.validSize < p_temp.tempFixCount) && !is_graph)
        {
            log("\n\rПотеряны результаты измерений температуры МК\n\r")
        }

        var delta = p_temp.tempFixCount - exec_fix_count_temp

        if ((delta < (p_temp.maxSize.toFloat() / 2.5).toUInt()))
        {
            sleep_count++;
        }

        if ((delta > (p_temp.maxSize.toFloat() / 1.5).toUInt()) && (sleep_count != 0u))
        {
            sleep_count--;
        }

        var count = min( delta, p_temp.validSize.toUInt() )
        var i : Int = p_temp.validSize.toInt()
        while(count-- > 0u)
        {
            temp_current = readFloatLE_temp(p_temp.buf, --i)
            temp_av      = (temp_av.toFloat() * cycle_av_temp.toFloat() + temp_current) / (cycle_av_temp.toFloat() + 1f);
            temp_min     = min ( temp_min, temp_current );
            temp_max     = max ( temp_max, temp_current );
            temp_quad   += (temp_current.toDouble() - TEMP_ABS.toDouble()).pow(2.0).toFloat();
            cycle_av_temp++;
        }

        if ((i != 0) && (exec_tick_stop_temp != 0UL) && (old_current_temp != readFloatLE_temp(p_temp.buf, (i-1))) && !is_graph)
        {
            log("\n\rНарушение данных в кольцевом буфере\n\r")
        }

        old_current_temp = readFloatLE_temp(p_temp.buf, (p_temp.validSize - 1u).toInt())

        exec_fix_count_temp = p_temp.tempFixCount;

        if ((exec_tick_stop_temp == 0UL) || call_last
            || ((accuracy_temp < 1e-99) && !TimeUtils.timeoutOk( exec_tick_stop_temp ))
            || ((accuracy_temp > 0) && ((1.0 / sqrt(cycle_av_temp.toDouble())) < accuracy_temp)))
        {
            temp_old = temp_av
            if (exec_tick_stop_temp == 0UL)
                exec_tick_stop_temp = TimeUtils.tickEnd("5");
            else
            {
                temp_rms  = sqrt( 1e-4 + temp_quad.toDouble() / cycle_av_temp.toDouble() - (temp_av.toDouble() - TEMP_ABS.toDouble()).pow(
                    2.0
                )
                ).toFloat();
                temp_rms /= sqrt( 1e-4 + cycle_av_temp.toDouble() ).toFloat();
//                temp_to_file( temp_av, temp_min, temp_max, temp_rms, call_last );
                exec_tick_stop_temp += (0.0f + 1e6f * 5f).toULong();
            }
        }
    }

    if(!is_graph)
    {
        if (temp_rms == 0f)
        {
            log("Температура МК = $temp_old °C")
        }
        else
        {
            log("Температура МК = $temp_old +- $temp_rms °C \b")
        }
    }

    return temp_old
}

@OptIn(ExperimentalUnsignedTypes::class)
fun readFloatLE_temp(data: UByteArray, index: Int): Float {
    val offset = index * 4 + 1

    fun getByte(pos: Int): Int {
        val wrappedPos = (pos % data.size + data.size) % data.size
        return data[wrappedPos].toInt() and 0xFF
    }

    val intBits = getByte(offset) or
            (getByte(offset + 1) shl 8) or
            (getByte(offset + 2) shl 16) or
            (getByte(offset + 3) shl 24)

    return Float.fromBits(intBits)
}