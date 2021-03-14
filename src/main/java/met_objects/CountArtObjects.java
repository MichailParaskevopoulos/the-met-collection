package met_objects;

import static com.google.cloud.hadoop.io.bigquery.BigQueryConfiguration.PROJECT_ID;
import static com.google.cloud.hadoop.io.bigquery.BigQueryConfiguration.SELECTED_FIELDS;
import static com.google.common.collect.Streams.stream;

import com.google.cloud.hadoop.io.bigquery.BigQueryConfiguration;
import com.google.cloud.hadoop.io.bigquery.BigQueryFileFormat;
import com.google.cloud.hadoop.io.bigquery.GsonBigQueryInputFormat;
import com.google.cloud.hadoop.io.bigquery.HadoopConfigurationProperty;
import com.google.cloud.hadoop.io.bigquery.output.BigQueryOutputConfiguration;
import com.google.cloud.hadoop.io.bigquery.output.BigQueryTableFieldSchema;
import com.google.cloud.hadoop.io.bigquery.output.BigQueryTableSchema;
import com.google.cloud.hadoop.io.bigquery.output.IndirectBigQueryOutputFormat;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class CountArtObjects {

  public static class Map extends Mapper<LongWritable, JsonObject, Text, Text> {

    private Text department = new Text();
    private Text objectType = new Text();
    
    public void setup(Mapper<LongWritable, JsonObject, Text, Text>.Context context) {}

    public void map(LongWritable key, JsonObject value, Mapper<LongWritable, JsonObject, Text, Text>.Context context) throws IOException, InterruptedException {
      JsonElement countDepartment = value.get("department");
      JsonElement countObject = value.get("object_name");
      
      if (countDepartment != null && countObject != null) {
        String DepartmentInRecord = countDepartment.getAsString();
        department.set(DepartmentInRecord);
        
        String objectTypeInRecord = countObject.getAsString();
        objectType.set(objectTypeInRecord);
        
        context.write(department, objectType);
      }
    }
  }

  public static class Reduce extends Reducer<Text, Text, JsonObject, NullWritable> {

    public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
      
      Set<String> hash_Set = new HashSet<String>();
      for (Text objectType : values) {
        hash_Set.add(objectType.toString());
      }
      
      JsonObject jsonObject = new JsonObject();
      jsonObject.addProperty("department", key.toString());
      jsonObject.addProperty("object_count", hash_Set.size());
      
      context.write(jsonObject, NullWritable.get());
    }
  }

  public static void main(String[] args)
      throws IOException, InterruptedException, ClassNotFoundException {

    GenericOptionsParser parser = new GenericOptionsParser(args);
    args = parser.getRemainingArgs();

    String projectId = args[0];
    String inputQualifiedTableId = args[1];
    String outputQualifiedTableId = args[2];
    String outputGcsPath = args[3];

    BigQueryTableSchema outputSchema =
        new BigQueryTableSchema()
            .setFields(
                ImmutableList.of(
                    new BigQueryTableFieldSchema().setName("department").setType("STRING"),
                    new BigQueryTableFieldSchema().setName("object_count").setType("INTEGER")));

    Job job = Job.getInstance(parser.getConfiguration(), "met_object_count");
    Configuration conf = job.getConfiguration();

    conf.set(PROJECT_ID.getKey(), projectId);

    BigQueryConfiguration.configureBigQueryInput(conf, inputQualifiedTableId);
    BigQueryOutputConfiguration.configure(
        conf,
        outputQualifiedTableId,
        outputSchema,
        outputGcsPath,
        BigQueryFileFormat.NEWLINE_DELIMITED_JSON,
        TextOutputFormat.class);

    conf.set(SELECTED_FIELDS.getKey(), "department,object_name");

    job.setJarByClass(CountArtObjects.class);

    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);

    job.setMapperClass(Map.class);
    job.setReducerClass(Reduce.class);

    job.setInputFormatClass(GsonBigQueryInputFormat.class);

    job.setOutputFormatClass(IndirectBigQueryOutputFormat.class);

    job.waitForCompletion(true);

    GsonBigQueryInputFormat.cleanupJob(job.getConfiguration(), job.getJobID());

  }
}
