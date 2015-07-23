package org.objenesis;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.objenesis.instantiator.basic.ClassDefinitionUtils.*;

/**
 * @author Henri Tremblay
 */
public class ClassReader {

   byte[] buffer = new byte[256];
   Object[] constant_pool;

   public static void main(String[] args) throws IOException {
      if(args.length != 1) {
         System.out.println("Usage: ClassReader class_name_including_package");
      }

      ClassReader reader = new ClassReader();
      reader.readClass(args[0]);
   }

   static class CONSTANT_Utf8_info {
      // int length; u2 is read by readUTF
      String bytes;

      CONSTANT_Utf8_info(DataInputStream in) throws IOException {
         bytes = in.readUTF();
      }

      @Override
      public String toString() {
         return "CONSTANT_Utf8_info{" +
            "bytes='" + bytes + '\'' +
            '}';
      }
   }

   static class CONSTANT_Methodref_info {
      int class_index; // u2
      int name_and_type_index; // u2

      CONSTANT_Methodref_info(DataInputStream in) throws IOException {
         class_index = in.readUnsignedShort();
         name_and_type_index = in.readUnsignedShort();
      }

      @Override
      public String toString() {
         return "CONSTANT_Methodref_info{" +
            "class_index=" + class_index +
            ", name_and_type_index=" + name_and_type_index +
            '}';
      }
   }

   static class CONSTANT_Class_info {
      int name_index; // u2

      public CONSTANT_Class_info(DataInputStream in) throws IOException{
         name_index = in.readUnsignedShort();
      }

      @Override
      public String toString() {
         return "CONSTANT_Class_info{" +
            "name_index=" + name_index +
            '}';
      }
   }

   static class CONSTANT_NameAndType_info {
      int name_index; // u2
      int descriptor_index; // u2

      public CONSTANT_NameAndType_info(DataInputStream in) throws IOException{
         name_index = in.readUnsignedShort();
         descriptor_index = in.readUnsignedShort();
      }

      @Override
      public String toString() {
         return "CONSTANT_NameAndType_info{" +
            "name_index=" + name_index +
            ", descriptor_index=" + descriptor_index +
            '}';
      }
   }

   class method_info {
      int access_flags; // u2
      int name_index;
      int descriptor_index;
      int attributes_count;
      attribute_info[] attributes;

      public method_info(DataInputStream in) throws IOException{
         access_flags = in.readUnsignedShort();
         name_index = in.readUnsignedShort();
         descriptor_index = in.readUnsignedShort();
         attributes_count = in.readUnsignedShort();
         attributes = new attribute_info[attributes_count];

         for (int i = 0; i < attributes_count; i++) {
            attributes[i] = new attribute_info(in);
         }
      }

      @Override
      public String toString() {
         return "method_info{" +
            "access_flags=" + access_flags +
            ", name_index=" + name_index +
            ", descriptor_index=" + descriptor_index +
            ", attributes_count=" + attributes_count +
            '}';
      }
   }

   class attribute_info {
      int attribute_name_index; // u2
      int attribute_length; // u4
      Object info;

      public attribute_info(DataInputStream in) throws IOException{
         attribute_name_index = in.readUnsignedShort();
         attribute_length = in.readInt();

         String attribute_name = ((CONSTANT_Utf8_info) constant_pool[attribute_name_index]).bytes;

         System.out.println(this +  " " + attribute_name);

         if("Code".equals(attribute_name)) {
            info = new Code_attribute(in);
         }
         else if("SourceFile".equals(attribute_name)) {
            assertEquals(2, attribute_length); // always 2
            info = new SourceFile_attribute(in);
         }
         else if("LineNumberTable".equals(attribute_name)) {
            // I don't care about that (only used for debugging) so I will skip
            in.read(buffer, 0, attribute_length);
         }
         else if("LocalVariableTable".equals(attribute_name)) {
            // I don't care about that (only used for debugging) so I will skip
            in.read(buffer, 0, attribute_length);
         }
         else {
            fail("Unknown attribute: " + attribute_name);
         }

         System.out.println("\t" + info);
      }

      @Override
      public String toString() {
         return "attribute_info{" +
            "attribute_name_index=" + attribute_name_index +
            ", attribute_length=" + attribute_length +
            '}';
      }
   }

   class Code_attribute {
      int max_stack; // u2
      int max_locals; // u2
      int code_length; // u4
      byte[] code; // length of code_length
      int exception_table_length; // u2 if will be 0, so we will skip the exception_table
      int attributes_count; // u2
      attribute_info[] attributes;

      Code_attribute(DataInputStream in) throws IOException {
         max_stack = in.readUnsignedShort();
         max_locals = in.readUnsignedShort();
         code_length = in.readInt();
         code = new byte[code_length];
         in.read(code);
         exception_table_length = in.readUnsignedShort();
         attributes_count = in.readUnsignedShort();
         attributes = new attribute_info[attributes_count];
         for (int i = 0; i < attributes_count; i++) {
            attributes[i] = new attribute_info(in);
         }
      }

      @Override
      public String toString() {
         return "Code_attribute{" +
            "max_stack=" + max_stack +
            ", max_locals=" + max_locals +
            ", code_length=" + code_length +
            ", code=" + Arrays.toString(code) +
            ", exception_table_length=" + exception_table_length +
            ", attributes_count=" + attributes_count +
            '}';
      }
   }

   static class SourceFile_attribute {
      int sourcefile_index;

      SourceFile_attribute(DataInputStream in) throws IOException {
         sourcefile_index = in.readUnsignedShort();
      }

      @Override
      public String toString() {
         return "SourceFile_attribute{" +
            "sourcefile_index=" + sourcefile_index +
            '}';
      }
   }

   public void readClass(String className) throws IOException {
      String resourceName = classNameToResource(className);
      InputStream resource = getClass().getClassLoader().getResourceAsStream(resourceName);

      DataInputStream in = new DataInputStream(resource);

      // magic number
      in.read(buffer, 0, MAGIC.length);
      assertArrayEquals(MAGIC);

      // version
      in.read(buffer, 0, VERSION.length);
      assertArrayEquals(VERSION);

      // constant_pool_count
      int constant_pool_count = in.readUnsignedShort();
      assertEquals(0x10, constant_pool_count);

      // indexed from 1 (0 will be unused) to constant_pool_count-1
      constant_pool = new Object[constant_pool_count];

      // constant pool
      for (int i = 1; i < constant_pool_count; i++) {
         System.out.print(i + ": ");
         int type = in.readUnsignedByte();
         switch(type) {
         case CONSTANT_Utf8:
            constant_pool[i] = new CONSTANT_Utf8_info(in);
            break;
         case CONSTANT_Class:
            constant_pool[i] = new CONSTANT_Class_info(in);
            break;
         case CONSTANT_Methodref:
            constant_pool[i] = new CONSTANT_Methodref_info(in);
            break;
         case CONSTANT_NameAndType:
            constant_pool[i] = new CONSTANT_NameAndType_info(in);
            break;
         default:
            fail("Unknown type: " + type);
         }
         System.out.println(constant_pool[i]);
      }

      // access flags
      int access_flags = in.readUnsignedShort();
      assertEquals(ACC_PUBLIC | ACC_SUPER, access_flags); // see http://stackoverflow.com/questions/8949933/what-is-the-purpose-of-the-acc-super-access-flag-on-java-class-files

      // this class name
      int this_class = in.readUnsignedShort();
      assertEquals(2, this_class);

      // super class name
      int super_class = in.readUnsignedShort();
      assertEquals(3, super_class);

      // interfaces implemented count (we have none)
      int interfaces_count = in.readUnsignedShort();
      assertEquals(0, interfaces_count); // so no interfaces

      // fields count (we have none)
      int fields_count = in.readUnsignedShort();
      assertEquals(0, fields_count); // so no fields

      //methods count (we have one)
      int methods_count = in.readUnsignedShort();
      assertEquals(1, methods_count); // the default constructor

      // reading the declaration of the default constructor
      method_info methodInfo = new method_info(in);
      assertEquals(ACC_PUBLIC, methodInfo.access_flags);
      assertEquals(4, methodInfo.name_index); // <init>
      assertEquals(5, methodInfo.descriptor_index); // ()V if specifies the parameters
      assertEquals(1, methodInfo.attributes_count); // only one attributes: the Code

      Code_attribute code = (Code_attribute) methodInfo.attributes[0].info;
      assertEquals(1, code.max_stack);
      assertEquals(1, code.max_locals);
      assertEquals(5, code.code_length);
      assertEquals(0, code.exception_table_length);
      assertEquals(2, code.attributes_count);

      // and the code
      assertEquals(OPS_aload_0, code.code[0]);
      assertEquals(OPS_invokespecial, code.code[1]);
      assertEquals(1, code.code[2] << 8 | code.code[3]); // should be one which is the index of the constructor of the superclass
      assertEquals(OPS_return, code.code[4]);

      // reading final class attributes
      System.out.println("Class attributes");
      int attributes_count = in.readUnsignedShort();
      assertEquals(1, attributes_count); // we have one, which is the source file name

      attribute_info attributeInfo = new attribute_info(in);
      assertEquals(12, ((SourceFile_attribute) attributeInfo.info).sourcefile_index);

      in.close();
   }

   private void assertArrayEquals(byte[] expected) {
      for (int i = 0; i < expected.length; i++) {
         if(expected[i] != buffer[i]) {
            fail("Expected was " + Arrays.toString(expected) + " but actual is " + Arrays.toString(buffer));
         }
      }
   }
}
