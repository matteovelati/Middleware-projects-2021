package it.polimi.middleware.spark;

import it.polimi.middleware.spark.utils.LogUtils;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.*;

public class Covid {
    private static final boolean useCache = true;

    public static void main(String[] args){
        LogUtils.setLogLevel();

        final String master = args.length > 0 ? args[0] : "local[4]";
        final String filePath = args.length > 1 ? args[1] : "./files/data/COVID-19-geographic-disbtribution-worldwide-2020-12-14.csv";

        final SparkSession spark = SparkSession
                .builder()
                .master(master)
                .appName("Covid-19")
                //.config("spark.sql.shuffle.partitions", 1)
                .getOrCreate();

        final List<StructField> mSchemaFields = new ArrayList<>();
        mSchemaFields.add(DataTypes.createStructField("dateRep", DataTypes.DateType, true));
        mSchemaFields.add(DataTypes.createStructField("day", DataTypes.IntegerType, true));
        mSchemaFields.add(DataTypes.createStructField("month", DataTypes.IntegerType, true));
        mSchemaFields.add(DataTypes.createStructField("year", DataTypes.IntegerType, true));
        mSchemaFields.add(DataTypes.createStructField("cases", DataTypes.IntegerType, true));
        mSchemaFields.add(DataTypes.createStructField("deaths", DataTypes.IntegerType, true));
        mSchemaFields.add(DataTypes.createStructField("countriesAndTerritories", DataTypes.StringType, true));
        mSchemaFields.add(DataTypes.createStructField("geoId", DataTypes.StringType, true));
        mSchemaFields.add(DataTypes.createStructField("countryTerritoryCode", DataTypes.StringType, true));
        mSchemaFields.add(DataTypes.createStructField("popData2019", DataTypes.IntegerType, true));
        mSchemaFields.add(DataTypes.createStructField("continentExp", DataTypes.StringType, true));
        final StructType mSchema = DataTypes.createStructType(mSchemaFields);

        long startTime = System.nanoTime();

        final Dataset<Row> covidData = spark
                .read()
                .option("header", "true")
                .option("delimiter", ";")
                .option("dateFormat", "dd/MM/yy")
                .schema(mSchema)
                .csv(filePath);

        long endTime_loading = System.nanoTime();

        // Q1.  Seven days moving average of new reported cases, for each country and for each day

        final Dataset<Row> dateCountry_ma = covidData
                .select(col("dateRep"), col("countriesAndTerritories"),
                        avg("cases").over(Window.partitionBy("countriesAndTerritories")
                        .orderBy("dateRep").rowsBetween(-6, 0)).as("7days_moving_average"))
                .orderBy( "countriesAndTerritories", "dateRep");

        if(useCache)
            dateCountry_ma.cache();

        // Q2.  Percentage increase (with respect to the day before) of the seven days moving average, for each country
        //      and for each day

        final Dataset<Row>  dateCountry_increase = dateCountry_ma
                .select(col("dateRep"), col("countriesAndTerritories"),
                        col("7days_moving_average").divide(lag(col("7days_moving_average"), 1)
                                .over(Window.partitionBy("countriesAndTerritories")
                                .orderBy("dateRep"))).multiply(100).minus(100).as("increase_day_before"))
                .orderBy("countriesAndTerritories", "dateRep")
                .na().fill(0);

        if(useCache)
            dateCountry_increase.cache();




        // Q3.  Top 10 countries with the highest percentage increase of the seven days moving average, for each day

        final Dataset<Row> topCountry_increase = dateCountry_increase
                .select(col("dateRep"), col("countriesAndTerritories"),
                        col("increase_day_before"),
                        row_number().over(Window.partitionBy("dateRep")
                        .orderBy(desc("increase_day_before"))).alias("rank"))
                .filter(col("rank").leq(10))
                .orderBy(desc("dateRep"), desc("increase_day_before"));


        dateCountry_ma.show();
        long endTime_q1 = System.nanoTime();

        dateCountry_increase.show();
        long endTime_q2 = System.nanoTime();

        topCountry_increase.show();
        long endTime_q3 = System.nanoTime();

        long endTime_output = System.nanoTime();


        System.out.println(
                "Loading time: " + (endTime_loading - startTime)/1000000 + "\n" +
                "Q1 execution time: " + (endTime_q1 - endTime_loading)/1000000 + "\n" +
                "Q2 execution time: " + (endTime_q2 - endTime_q1)/1000000 + "\n" +
                "Q3 execution time: " + (endTime_q3 - endTime_q2)/1000000 + "\n" +
                "Total time: " + (endTime_output - startTime)/1000000);
        spark.close();

    }
}
