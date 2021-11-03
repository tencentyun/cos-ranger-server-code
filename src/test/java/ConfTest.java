import org.apache.hadoop.conf.Configuration;

public class ConfTest {
    public static void main(String[] args) {
        Configuration conf = new Configuration();
        System.setProperty("hello", "world");

        String value = conf.get("hello");
        System.out.println(value);
    }
}
