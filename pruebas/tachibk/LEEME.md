# Prueba de compatibilidad .tachibk

Comprueba que `Proto.kt` + `TachibkFormato.kt` se entienden con Mihon/Komikku en los dos sentidos, usando la misma
librería que ellos (kotlinx-serialization-protobuf) y sus mismos modelos (`Prueba.kt`).

```bash
# jars: kotlin-serialization-compiler-plugin-embeddable-2.0.21, kotlinx-serialization-core-jvm, kotlinx-serialization-protobuf-jvm
kotlinc -Xplugin=kotlin-serialization-compiler-plugin-embeddable-2.0.21.jar \
  -cp kotlinx-serialization-core-jvm-1.7.3.jar:kotlinx-serialization-protobuf-jvm-1.7.3.jar \
  ../../app/src/main/java/com/example/lector/Proto.kt ../../app/src/main/java/com/example/lector/TachibkFormato.kt *.kt -d out
kotlin -cp out:kotlinx-serialization-core-jvm-1.7.3.jar:kotlinx-serialization-protobuf-jvm-1.7.3.jar prueba.PruebaKt
```
Debe terminar con `TODO OK`.
