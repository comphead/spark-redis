package org.apache.spark.sql.redis

import java.lang.{Boolean => JBoolean, Byte => JByte, Double => JDouble, Float => JFloat, Long => JLong}

import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.types._
import redis.clients.jedis.Pipeline

import scala.collection.JavaConverters._

/**
  * @author The Viet Nguyen
  */
class HashRedisPersistence extends RedisPersistence[Map[String, String]] {

  override def save(pipeline: Pipeline, key: String, value: Map[String, String]): Unit =
    pipeline.hmset(key, value.asJava)

  override def load(pipeline: Pipeline, key: String, requiredColumns: Seq[String]): Unit = {
    if (requiredColumns.isEmpty) {
      pipeline.hgetAll(key)
    } else {
      pipeline.hmget(key, requiredColumns: _*)
    }
  }

  override def encodeRow(value: Row): Map[String, String] = {
    val fields = value.schema.fields.map(_.name)
    val kvMap = value.getValuesMap[Any](fields)
    kvMap.map { case (k, v) =>
      k -> String.valueOf(v)
    }
  }

  override def decodeRow(value: Map[String, String], schema: => StructType,
                         inferSchema: Boolean): Row = {
    val actualSchema = if (!inferSchema) schema else {
      val fields = value.keys
        .map(StructField(_, StringType))
        .toArray
      StructType(fields)
    }
    val fieldsValue = parseFields(value, actualSchema)
    new GenericRowWithSchema(fieldsValue, actualSchema)
  }

  private def parseFields(value: Map[String, String], schema: StructType): Array[Any] =
    schema.fields.map { field =>
      val fieldName = field.name
      val fieldValue = value(fieldName)
      parseValue(field.dataType, fieldValue)
    }

  private def parseValue(dataType: DataType, fieldValueStr: String): Any =
    dataType match {
      case ByteType => JByte.parseByte(fieldValueStr)
      case IntegerType => Integer.parseInt(fieldValueStr)
      case LongType => JLong.parseLong(fieldValueStr)
      case FloatType => JFloat.parseFloat(fieldValueStr)
      case DoubleType => JDouble.parseDouble(fieldValueStr)
      case BooleanType => JBoolean.parseBoolean(fieldValueStr)
      case _ => fieldValueStr
    }
}