package lib;

import java.io.*;
import java.util.Random;

/**
 * The Helper function for RaftNode
 */
public class Helper {
    public static int heartBeatFreq = 150;
    public static int MAX_ELECTION_TIMEOUT = 600;
    public static int MIN_ELECTION_TIMEOUT = 300;
    public static boolean VERBOSE = false;

    /**
     * The generating function for the random election_time_out
     * @param lowrange the low bound of the election_time_out
     * @param highrange the high bound of the election_time_out
     * @return int Random election_time_out
     */
    public static int RandomTimeout(int lowrange, int highrange){
        Random random = new Random();
        return random.nextInt(highrange - lowrange) + lowrange;
    }

    /**
     * <a href = "https://stackoverflow.com/questions/3736058/java-object-to-byte-and-byte-to-object-converter-for-tokyo-cabinet/3736091">
     *    Reference: object byte converting </a>
     * @param object object to be converted to byte
     * @return byte Array
     */
    public static byte[] toByteConverter(Object object) {
        ByteArrayOutputStream byte_outstream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream obj_outstream = new ObjectOutputStream(byte_outstream);
            obj_outstream.writeObject(object);
            obj_outstream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byte_outstream.toByteArray();
    }


    /**
     * <a href = "https://stackoverflow.com/questions/3736058/java-object-to-byte-and-byte-to-object-converter-for-tokyo-cabinet/3736091">
     *     Reference: object byte converting </a>
     * @param bytes byte Array to be convert to
     * @return Object
     */
    public static Object toObjectConverter(byte[] bytes) {
        ByteArrayInputStream byte_instream = new ByteArrayInputStream(bytes);
        Object object = null;
        try {
            ObjectInputStream obj_instream = new ObjectInputStream(byte_instream);
            object = obj_instream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return object;
    }

    /**
     * the Printing debug Log function, can be used with command line
     * for example, <code>java textname port &gt; debuglog.txt</code>
     * @param string the formatted string
     */
    public static void debugLog(String string){
        if (VERBOSE) {
            System.out.printf(
                    string
            );
            System.out.flush();
        }
    }

}
