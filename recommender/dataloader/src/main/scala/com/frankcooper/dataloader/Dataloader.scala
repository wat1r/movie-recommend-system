package com.frankcooper.dataloader

import java.net.InetAddress

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.client.PreBuiltTransportClient

/**
  *
  * Created by FrankCooper
  * Date 2019/4/27 16:18
  * Description
  */

case class Movie(val mid: Int, val name: String, val descri: String, val timelong: String, val issue: String,
                 val shoot: String, val language: String, val genres: String, val actors: String, val directors: String)

case class Rating(val uid: Int, val mid: Int, val score: Double, val timestamp: Int)

case class Tag(val uid: Int, val mid: Int, val tag: String, val timestamp: Int)


/**
  * MongoDB的配置
  *
  * @param uri MongoDB的连接
  * @param db  MongoDB要操作的数据库
  */
case class MongoConfig(val uri: String, val db: String)


/**
  * ElasticSearch
  *
  * @param httpHosts      http的主机列表，以,分类
  * @param transportHosts transport主机列表，以,分类
  * @param index          需要操作的索引
  * @param clustername    ES集群的名称
  */
case class ESConfig(val httpHosts: String, val transportHosts: String, val index: String, val clustername: String)


object Dataloader {

  val MOVIE_DATA_PATH = "E:\\StudyingCourse\\IntellijModule\\movie-recommend-system\\recommender\\dataloader\\src\\main\\resources\\movies.csv"
  val RATING_DATA_PATH = "E:\\StudyingCourse\\IntellijModule\\movie-recommend-system\\recommender\\dataloader\\src\\main\\resources\\ratings.csv"
  val TAG_DATA_PATH = "E:\\StudyingCourse\\IntellijModule\\movie-recommend-system\\recommender\\dataloader\\src\\main\\resources\\tags.csv"


  val MONGODB_MOVIE_COLLECTION = "Movie"
  val MONGODB_RATING_COLLECTION = "Rating"
  val MONGODB_TAG_COLLECTION = "Tag"

  val ES_MOVIE_INDEX = "Movie"


  //  程序入口
  def main(args: Array[String]): Unit = {

    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://192.168.200.134:27017/recommender",
      "mongo.db" -> "recommender",
      "es.httpHosts" -> "192.168.200.134:9200",
      "es.transportHosts" -> "192.168.200.134:9300",
      "es.index" -> "recommender",
      "es.cluster.name" -> "es-cluster"
    )

    // 声明Spark的配置信息
    val sparkConf = new SparkConf().setAppName("Dataloader").setMaster(config.get("spark.cores").get)

    // 创建SparkSession
    val spark = SparkSession.builder().config(conf = sparkConf).getOrCreate()

    import spark.implicits._

    //    将movie rating tag数据集加载进来
    val movieRDD = spark.sparkContext.textFile(MOVIE_DATA_PATH)
    //    将movieRDD转换为DataFrame
    val movieDF = movieRDD.map(item => {
      val attr = item.split("\\^")
      Movie(attr(0).toInt, attr(1).trim, attr(2).trim, attr(3).trim, attr(4).trim, attr(5).trim, attr(6).trim, attr(7).trim, attr(8).trim, attr(9).trim)
    }).toDF()


    val ratingRDD = spark.sparkContext.textFile(RATING_DATA_PATH)
    val ratingDF = ratingRDD.map(item => {
      val attr = item.split("\\,")
      Rating(attr(0).toInt, attr(1).toInt, attr(2).toDouble, attr(3).toInt)
    }).toDF()

    val tagRDD = spark.sparkContext.textFile(TAG_DATA_PATH)
    val tagDF = tagRDD.map(item => {
      val attr = item.split("\\,")
      Tag(attr(0).toInt, attr(1).toInt, attr(2).trim, attr(3).toInt)
    }).toDF()

    implicit val mongoConfig = MongoConfig(config.get("mongo.uri").get, config.get("mongo.db").get)

    //    需要将数据保存在mongodb中
    //    storeDataInMongonDB(movieDF, ratingDF, tagDF)


    //    首先需要将Tag数据集进行处理，处理后的形式为mid,tag1|tag2|tag3....
    import org.apache.spark.sql.functions._
    /**
      * MID,Tags
      * 1   tag1|tag2|tag3....
      */
    val newTag = tagDF.groupBy($"mid").agg(concat_ws("|", collect_set($"tag")).as("tags")).select("mid", "tags")

    //    需要将处理后的Tag数据，和Movie数据融合。产生新的Movie数据
    val movieWithTagsDF = movieDF.join(newTag, Seq("mid", "mid"), "left")

    implicit val eSConfig = ESConfig(config.get("es.httpHosts").get,
      config.get("es.transportHosts").get, config.get("es.index").get,
      config.get("es.cluster.name").get)


    //    将新的Movie数据保存到ES中
    //需要将数据保存在ES中
    storeDataInES(movieWithTagsDF)

    //关闭spark
    spark.stop()

  }

  /**
    * 将数据保存在mongodb中
    */
  def storeDataInMongonDB(movieDF: DataFrame, ratingDF: DataFrame, tagDF: DataFrame)(implicit mongoConfig: MongoConfig): Unit = {
    //    新建一个MongoDB的连接
    val mongoClient = MongoClient(MongoClientURI(mongoConfig.uri))

    //如果MongoDB中有对应的数据库，删除
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).dropCollection()

    //
    //将Movie数据集写入到MongoDB
    movieDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_MOVIE_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //将Rating数据集写入到MongoDB
    ratingDF
      .write.option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //将Tag数据集写入到MongoDB
    tagDF
      .write.option("uri", mongoConfig.uri)
      .option("collection", MONGODB_TAG_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //创建索引
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("uid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("uid" -> 1))



    //
    mongoClient.close()
  }


  /**
    * 将数据保存在ES中
    */
  def storeDataInES(movieDF: DataFrame)(implicit eSConfig: ESConfig): Unit = {
    //    新建一个配置
    val settings: Settings = Settings.builder().put("cluster.name", eSConfig.clustername).build()


    //新建ES的客户端
    val REGEX_HOST_PORT = "(.+):(\\d+)".r
    val esClient = new PreBuiltTransportClient(settings)
    eSConfig.transportHosts.split(",").foreach {
      case REGEX_HOST_PORT(host: String, port: String) => {
        esClient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port.toInt))
      }
    }
    //需要清除掉ES中的遗留数据
    if (esClient.admin().indices().exists(new IndicesExistsRequest(eSConfig.index)).actionGet().isExists) {
      esClient.admin().indices().delete(new DeleteIndexRequest(eSConfig.index))
    }

    esClient.admin().indices().create(new CreateIndexRequest(eSConfig.index))

    //    将数据写入ES中
    // 声明写出时的ES配置信息
    val movieOptions = Map("es.nodes" -> eSConfig.httpHosts,
      "es.http.timeout" -> "100m",
      "es.mapping.id" -> "mid")


    // 标签数据写出时的Type名称【表】
    //val tagTypeName = s"$indexName/$ES_TAG_TYPE_NAME"
    // 将Movie信息保存到ES
    movieDF
      .write.options(movieOptions)
      .mode("overwrite")
      .format("org.elasticsearch.spark.sql")
      .save(eSConfig.index + "/" + ES_MOVIE_INDEX)


  }

}
