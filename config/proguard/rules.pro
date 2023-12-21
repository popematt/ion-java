# See https://www.guardsquare.com/manual/configuration/usage
-keep,includecode class !com.amazon.ion.shaded_.** { *; }
-keepattributes Signature,Exceptions,*Annotation*,
                InnerClasses,PermittedSubclasses,EnclosingMethod,
                Deprecated,SourceFile,LineNumberTable
-dontoptimize
-dontobfuscate
-dontwarn java.sql.Timestamp
