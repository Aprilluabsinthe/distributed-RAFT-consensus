package lib;

import java.io.*;
import java.util.Random;

public class Helper {
    public static int heartBeatFreq = 150;
    public static int MAX_ELECTION_TIMEOUT = 600;
    public static int MIN_ELECTION_TIMEOUT = 300;
    public static boolean VERBOSE = true;

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

    public static void debugLog(String string){
        if (VERBOSE) {
            System.out.printf(
                    string
            );
            System.out.flush();
        }
    }

}
