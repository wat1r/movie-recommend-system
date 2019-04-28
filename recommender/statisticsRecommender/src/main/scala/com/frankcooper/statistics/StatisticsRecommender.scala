package com.frankcooper.statistics

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession


/**
  *
  * Created by FrankCooper
  * Date 2019/4/27 21:29
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
  * 推荐对象
  *
  * @param rid 推荐的Movie的mid
  * @param r   Movie的评分
  */
case class Recommendation(rid: Int, r: Double)

/**
  * 电影类别的推荐
  *
  * @param genres 电影的类别
  * @param recs   top10的电影的集合
  */
case class GenresRecommendation(genres: String, recs: Seq[Recommendation])


object StatisticsRecommender {

  val MONGODB_MOVIE_COLLECTION = "Movie"
  val MONGODB_RATING_COLLECTION = "Rating"


  //统计的表的名称
  val RATE_MORE_MOVIES = "RateMoreMovies"
  val RATE_MORE_RECENTLY_MOVIES = "RateMoreRecentlyMovies"
  val AVERAGE_MOVIES = "AverageMovies"
  val GENRES_TOP_MOVIES = "GenresTopMovies"

  def main(args: Array[String]): Unit = {

    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://192.168.200.134:27017/recommender",
      "mongo.db" -> "recommender"
    )

    //创建SparkConf
    val sparkConf = new SparkConf().setAppName("StatisticsRecommender").setMaster(config("spark.cores"))

    //创建SparkSession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()


    val mongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    //    数据加载进来
    import spark.implicits._
    //数据加载进来
    val ratingDF = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Rating]
      .toDF()

    val movieDF = spark
      .read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]
      .toDF()


    ratingDF.createOrReplaceTempView("ratings")
    //    统计所有历史数据中每个电影的评分数
    val rateMoreMoviesDF = spark.sql("select mid, count(mid) as count from ratings group by mid")

    rateMoreMoviesDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", RATE_MORE_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()
    //    统计以月为单位每个电影的评分数
    //创建一个格式化的日期工具
    val simpleDateFormat = new SimpleDateFormat("yyyyMM")
    spark.udf.register("changeDate", (x: Int) => simpleDateFormat.format(new Date(x * 1000L)).toInt)
    // 将原来的Rating数据集中的时间转换成年月的格式
    val ratingOfYeahMonth = spark.sql("select mid, score, changeDate(timestamp) as yeahmonth from ratings")

    // 将新的数据集注册成为一张表
    ratingOfYeahMonth.createOrReplaceTempView("ratingOfMonth")

    val rateMoreRecentlyMovies = spark.sql("select mid, count(mid) as count ,yeahmonth from ratingOfMonth group by yeahmonth,mid")

    rateMoreRecentlyMovies
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", RATE_MORE_RECENTLY_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //    统计每个电影的平均评分
    val averageMoviesDF = spark.sql("select mid, avg(score) as avg from ratings group by mid")
    averageMoviesDF
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", AVERAGE_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //    统计每种电影类型中评分最高的Top10电影
    //需要用left join，应为只需要有评分的电影数据集
    val movieWithScore = movieDF.join(averageMoviesDF, Seq("mid", "mid"))

    //所有的电影类别
    val genres = List("Action", "Adventure", "Animation", "Comedy", "Ccrime", "Documentary", "Drama", "Family", "Fantasy", "Foreign", "History", "Horror", "Music", "Mystery"
      , "Romance", "Science", "Tv", "Thriller", "War", "Western")

    //将电影类别转换成RDD
    val genresRDD = spark.sparkContext.makeRDD(genres)

    //计算电影类别Top10
    val genrenTopMovies = genresRDD.cartesian(movieWithScore.rdd)
      .filter {
        // 过滤掉电影的类别不匹配的电影
        case (genres, row) => row.getAs[String]("genres").toLowerCase.contains(genres.toLowerCase)
      }.map {
      case (genres, row) => {
        // 将整个数据集的数据量减小，生成RDD[String,Iter[mid,avg]]
        (genres, (row.getAs[Int]("mid"), row.getAs[Double]("avg")))
      }
    }.groupByKey()
      .map {
        // 通过评分的大小进行数据的排序，然后将数据映射为对象
        case (genres, items) => GenresRecommendation(genres, items.toList.sortWith(_._2 > _._2).take(10).map(item => Recommendation(item._1, item._2)))
      }.toDF()


    // 输出数据到MongoDB
    genrenTopMovies
      .write
      .option("uri", mongoConfig.uri)
      .option("collection", GENRES_TOP_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //    关闭spark
    spark.stop()
  }
}
