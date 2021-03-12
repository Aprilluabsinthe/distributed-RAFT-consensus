package lib;

import java.io.*;
import java.rmi.RemoteException;
import java.util.Random;
import java.util.TimerTask;

public class Helper {
    public static int heartBeatFreq = 100;
    public static int MAX_ELECTION_TIMEOUT = 600;
    public static int MIN_ELECTION_TIMEOUT = 300;

    public static int RandomTimeout(int lowrange, int highrange){
        Random random = new Random();
        return random.nextInt(highrange - lowrange) + lowrange;
    }

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

}
