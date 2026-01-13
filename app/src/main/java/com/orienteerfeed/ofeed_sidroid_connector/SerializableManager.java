package com.orienteerfeed.ofeed_sidroid_connector;

import android.content.Context;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class SerializableManager {

    /**
     * Saves a serializable object.
     *
     * @param context      The application context.
     * @param objectToSave The object to save.
     * @param fileName     The name of the file.
     * @param <T>          The type of the object.
     * @return true if the object was successfully saved, else false.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static <T extends Serializable> boolean save(Context context, T objectToSave, String fileName) {
        try (ObjectOutputStream out = new ObjectOutputStream(context.openFileOutput(fileName, Context.MODE_PRIVATE))){
            out.writeObject(objectToSave);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Loads a serializable object.
     *
     * @param context  The application context.
     * @param fileName The filename.
     * @param <T>      The object type.
     * @return The serializable object if successful, else null.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T load(Context context, String fileName) {
        T objectToReturn;
        try (ObjectInputStream in = new ObjectInputStream(context.openFileInput(fileName))){
            objectToReturn = (T) in.readObject();   // Unchecked conversion.
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
        return objectToReturn;
    }

    /**
     * Deletes a specified file.
     *
     * @param context  The application context.
     * @param filename The name of the file.
     */
    @SuppressWarnings("unused")
    public static void delete(Context context, String filename) {
        context.deleteFile(filename);
    }

}