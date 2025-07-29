package com.example.usb_sector_rw.msd

import ParamCrcStruct
import ParamCrcStruct.Companion.PARAM_BUF_SIZE
import ParamUsbStruct
import ParamUsbStruct.Companion.PARAM_USB_STRUCT_MAX_SIZE
import PramUvStruct
import SectorAnswer
import SectorCmd
import android.annotation.SuppressLint
import com.example.usb_sector_rw.answer
import java.lang.Math.pow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.UByteArray
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

var answer = SectorAnswer()
var cycle_ok : ULong = 0UL
var fix_count : ULong = 0UL

var uv_range_value : UInt = 0u

var accuracy : Float = 0f
var uv_current : Float   = 0f; //$< Текущий измеренный уровень ультрафиолета, Вольт
var old_current : Float  = 0f; //$< Последний в предыдущей выборке измеренный уровень ультрафиолета, Вольт
var uv_old : Float       = 0f; //$< Предыдущий усредненный уровень ультрафиолета, Вольт
var uv_av : Float        = 0f; //$< Усредняемый уровень ультрафиолета, Вольт
var uv_quad : Float      = 0f; //$< Сумма квадратов измеренного уровня ультрафиолета
var uv_min : Float       = 999f; //$< Минимальный уровень ультрафиолета при усреднении, Вольт
var uv_max : Float       = -199f; //$< Максимальный уровень ультрафиолета при усреднении, Вольт
var uv_rms : Float       = 0f; //$< Среднеквадратичное отклонение при усреднении, Вольт
var cycle_av :ULong     = 0UL; //$< Число проведенных усреднений уровня ультрафиолета
var exec_fix_count :UInt    = 0u; //$< Обработанное число измерений уровня ультрафиолета
var tick_stop :ULong    = 0UL; //$< Время завершения усреднений в формате UTC
var adc_code_av : Float  = 0f; //$< Усредняемый код АЦП
var old_adc_code : Float = 0f; //$< Последний в предыдущей выборке измеренный уровень ультрафиолета, Вольт
var old_uv_rms : Float   = 0f;

fun get_uv(lospDev : LospDev, log: (String) -> Unit)
{
    val cmd = SectorCmd()
    cmd.code = CmdToPram.PRAM_GET_UV.value
    cmd.sizeOut = PramUvStruct.SIZE_BYTES.toUShort()

    lospDev.lospExecCmd(cmd, log)
    lospDev.getLospAnswer(cmd.code, answer, log)
}

@OptIn(ExperimentalUnsignedTypes::class)
fun uv_test(lospDev : LospDev, log: (String) -> Unit) : Boolean
{
    var loss : Boolean = false
    val cmd = SectorCmd()
    cmd.code = CmdToPram.PRAM_GET_UV.value
    cmd.sizeOut = PramUvStruct.SIZE_BYTES.toUShort()

    lospDev.lospExecCmd(cmd, log)
    lospDev.getLospAnswer(cmd.code, answer, log)

    var p = PramUvStruct.fromByteArray(answer.dataOut)

    loss = ((cycle_ok++ > 0UL) || (fix_count > 0UL)) && (p.uvFixCount > fix_count + p.validSize);
    fix_count = p.uvFixCount.toULong();

    if(!loss)
    {
        log("Потеряны результаты измерений уровня ультрафиолета")
    }
    else
    {
        log("Тест измерений уровня ультрафиолета №$cycle_ok - Ok")
    }

    return loss
}

@OptIn(ExperimentalUnsignedTypes::class)
fun set_uv_range(range : UInt, lospDev : LospDev, log: (String) -> Unit) : Boolean // TODO don't work
{
    var is_failed : Boolean = true

    uv_range_value = range

    val cmd = SectorCmd()
    cmd.code = CmdToPram.PRAM_SET_CURRENT_PARAM.value
    cmd.sizeOut = ParamUsbStruct.PARAM_USB_STRUCT_MAX_SIZE.toUShort()

    var prm : ParamUsbStruct = ParamUsbStruct(
        structType = StructLospDataType.STRUCT_CURRENT_PARAM.code,
        structSize = ParamUsbStruct.PARAM_USB_STRUCT_MAX_SIZE.toUShort(),
        paramCrc = 0u,
        st = ParamCrcStruct(
            Crc32 = 0u,
            ParamType = 0u,
            ParamSize = 0u,
            NameID = 0u,
            DateEdit = 1u,
            data = ByteArray(PARAM_BUF_SIZE)
        ),
        data = ByteArray(PARAM_USB_STRUCT_MAX_SIZE - ParamCrcStruct.SIZE_IN_BYTES - 4 - 2 - 2)
    )

    prm.st.ParamType  = ParamAdjId.PARAM_UV_RANGE_ID.id.toUShort()
    //$ Размер подструктуры настраиваемых параметров без обобщенного буфера.
    prm.st.ParamSize = 16u;
    prm.st.NameID    = 0u; // Хост этого не знает.
    insertUvRangeValueToData(prm.st.data, uv_range_value)
    prm.st.DateEdit   = 0x68765432u; // Только бы пройти контроль

    cmd.dataIn = prm.toByteArray()

    lospDev.lospExecCmd(cmd, log)
    lospDev.getLospAnswer(cmd.code, answer, log)

//    var p = ParamUsbStruct.fromByteArray(answer.dataOut)


//    if ((p.structType != StructLospDataType.STRUCT_CURRENT_PARAM.code)  || (p.structSize != ParamUsbStruct.SIZE_IN_BYTES.toUShort()))
//    {
//        log("\n\rОшибка структуры диапазона измерений ультрафиолета\n\r")
//        is_failed = true
//    }
//    var i = 0
//    for (b in answer.dataOut) {
//        log("data[$i] = $b")
//        i++
//    }

    if (answer.ret != RetFromPram.PRAM_MSD_OK.value)
    {
        log("\n\r Ошибка установки диапазона измерений ультрафиолета\n\r")
        is_failed = true
    }
    else
    {
        log("\r\n Установлен диапазон (0x%02x) измерений ультрафиолета\r\n")
    }

    return is_failed
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalUnsignedTypes::class)
fun uv_exec(lospDev : LospDev, log: (String) -> Unit, call_last : Boolean, is_graph : Boolean) : Float
{
    get_uv(lospDev, log)

    var p_uv = PramUvStruct.fromByteArray(answer.dataOut)

    if (exec_fix_count != p_uv.uvFixCount)
    {
        if ((cycle_av != 0UL))
        {
            cycle_av    = 0UL
            uv_min      = 1e9f
            uv_max      = -1e3f
            uv_quad     = 0f
            adc_code_av = 0f
            exec_fix_count   = p_uv.uvFixCount

            if(!is_graph)
            {
                log("\r\n Новый диапазон измерений ультрафиолета - ${p_uv.uvRange}\r\n")
            }

            return -1f
        }
    }

    if ((tick_stop != 0UL) && (fix_count + p_uv.validSize < p_uv.uvFixCount) && !is_graph)
    {
        log("\n\rПотеряны результаты измерений уровня ультрафиолета\n\r")
    }

    var delta : UInt = p_uv.uvFixCount.toUInt() - fix_count.toUInt();
    var count : Int  = min(delta.toInt(), p_uv.validSize.toInt());
    var i : Int = p_uv.validSize.toInt() - 0;

    while(count-- > 0)
    {
        uv_current  = readFloatLE(p_uv.buf, --i)
        uv_av       = (uv_av * cycle_av.toFloat() + uv_current) / (cycle_av.toFloat() + 1)
        uv_min      = min ( uv_min, uv_current )
        uv_max      = max ( uv_max, uv_current )
        uv_quad    += uv_current.toDouble().pow(2.0).toFloat()
        adc_code_av = (adc_code_av * cycle_av.toFloat() + p_uv.uvCode1) / (cycle_av.toFloat() + 1)
        cycle_av++
    }

    if ((i != 0) && (tick_stop != 0UL)
    && (old_current != (readFloatLE(p_uv.buf, (i-1)))) && !is_graph)
    {
        log("\n\rНарушение данных в кольцевом буфере\n\r")
    }

    old_current = readFloatLE(p_uv.buf, p_uv.validSize.toInt() - 1)

    exec_fix_count = p_uv.uvFixCount;

    if ((tick_stop == 0UL) || call_last
        || ((accuracy < 1e-99) && !TimeUtils.timeoutOk( tick_stop ))
        || ((accuracy > 0) && ((1.0 / sqrt(cycle_av.toFloat())) < accuracy)))
    {
        uv_old = uv_av;
        old_adc_code = adc_code_av;

        if (tick_stop == 0UL)
            tick_stop = TimeUtils.tickEnd("5");
        else
        {
            uv_rms  = sqrt( 1e-99 + abs( uv_quad / cycle_av.toFloat() - uv_av.toDouble().pow(2.0))).toFloat()
            uv_rms /= sqrt( 1e-99 + cycle_av.toDouble() ).toFloat()
            old_uv_rms = uv_rms;
//            uv_to_file( uv_av, uv_rms, call_last );
            tick_stop += (0.5 + 1e6 * max( 0.01f, 5f)).toULong();
        }
    }

    val str = "\ru"

    if(!is_graph)
    {
        if (old_uv_rms == 0f) {
            log(
                String.format(
                    "%sv_%d = %.1e Вт  (%.1f)",
                    str,
                    p_uv.uvRange.toInt(),
                    uv_old,
                    old_adc_code
                )
            )
        } else if ((old_uv_rms / uv_old) > 1e-3f) {
            log(
                String.format(
                    "%sv_%d = %.2e Вт ± %.2f%%  (%.0f ± %.1f)",
                    str,
                    p_uv.uvRange.toInt(),
                    uv_old,
                    100 * old_uv_rms / uv_old,
                    old_adc_code,
                    old_adc_code * old_uv_rms / uv_old
                )
            )
        } else if ((old_uv_rms / uv_old) > 1e-4f) {
            log(
                String.format(
                    "%sv_%d = %.3e Вт ± %.3f%%  (%.1f ± %.2f)",
                    str,
                    p_uv.uvRange.toInt(),
                    uv_old,
                    100 * old_uv_rms / uv_old,
                    old_adc_code,
                    old_adc_code * old_uv_rms / uv_old
                )
            )
        } else {
            log(
                String.format(
                    "%sv_%d = %.4e Вт ± %.4f%%  (%.2f ± %.3f)",
                    str,
                    p_uv.uvRange.toInt(),
                    uv_old,
                    100 * old_uv_rms / uv_old,
                    old_adc_code,
                    old_adc_code * old_uv_rms / uv_old
                )
            )
        }
    }

    return uv_old
}

@OptIn(ExperimentalUnsignedTypes::class)
fun readFloatLE(data: UByteArray, index: Int): Float {
    val offset = index * 4

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

fun insertUvRangeValueToData(data: ByteArray, uvRangeValue: UInt) {
    val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(uvRangeValue.toInt())

    val bytes = buffer.array()
    for (i in bytes.indices) {
        data[i] = bytes[i]
    }
}