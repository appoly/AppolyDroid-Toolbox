# ComposeExtensions ships Serializable MutableState holders (SerializableMutableState,
# TransientMutableState) that are Java-serialized inside Voyager Screens to survive process
# death. Java serialization invokes writeObject/readObject/serialVersionUID reflectively, so R8
# must be told to keep them — otherwise the value silently restores as null (NPE on next read)
# in minified consumer builds. Scoped to this package so we don't impose keeps on the consumer's
# own Serializable classes.
-keepclassmembers class uk.co.appoly.droid.compose.extensions.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    private void readObjectNoData();
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
