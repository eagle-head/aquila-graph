# Java Virtual Machine Specification — Java SE 25

[Source map](../README.md)

Edition: Java SE 25. This is a navigation index of the official JVMS sections, examples, and appendices.
Every content link opens Oracle; no specification body is reproduced here.

[Official contents][map-link-1]

## Complete contents

- [1. Introduction][map-link-2]
  - [1.1. A Bit of History][map-link-3]
  - [1.2. The Java Virtual Machine][map-link-4]
  - [1.3. Organization of the Specification][map-link-5]
  - [1.4. Notation][map-link-6]
  - [1.5. Preview Features][map-link-7]
    - [1.5.1. Restrictions on the Use of Preview Features][map-link-8]
    - [1.5.2. Current Preview VM Features][map-link-9]
  - [1.6. Feedback][map-link-10]
- [2. The Structure of the Java Virtual Machine][map-link-11]
  - [2.1. The `class` File Format][map-link-12]
  - [2.2. Data Types][map-link-13]
  - [2.3. Primitive Types and Values][map-link-14]
    - [2.3.1. Integral Types and Values][map-link-15]
    - [2.3.2. Floating-Point Types and Values][map-link-16]
    - [2.3.3. The `returnAddress` Type and Values][map-link-17]
    - [2.3.4. The `boolean` Type][map-link-18]
  - [2.4. Reference Types and Values][map-link-19]
  - [2.5. Run-Time Data Areas][map-link-20]
    - [2.5.1. The `pc` Register][map-link-21]
    - [2.5.2. Java Virtual Machine Stacks][map-link-22]
    - [2.5.3. Heap][map-link-23]
    - [2.5.4. Method Area][map-link-24]
    - [2.5.5. Run-Time Constant Pool][map-link-25]
    - [2.5.6. Native Method Stacks][map-link-26]
  - [2.6. Frames][map-link-27]
    - [2.6.1. Local Variables][map-link-28]
    - [2.6.2. Operand Stacks][map-link-29]
    - [2.6.3. Dynamic Linking][map-link-30]
    - [2.6.4. Normal Method Invocation Completion][map-link-31]
    - [2.6.5. Abrupt Method Invocation Completion][map-link-32]
  - [2.7. Representation of Objects][map-link-33]
  - [2.8. Floating-Point Arithmetic][map-link-34]
  - [2.9. Special Methods][map-link-35]
    - [2.9.1. Instance Initialization Methods][map-link-36]
    - [2.9.2. Class Initialization Methods][map-link-37]
    - [2.9.3. Signature Polymorphic Methods][map-link-38]
  - [2.10. Exceptions][map-link-39]
  - [2.11. Instruction Set Summary][map-link-40]
    - [2.11.1. Types and the Java Virtual Machine][map-link-41]
    - [2.11.2. Load and Store Instructions][map-link-42]
    - [2.11.3. Arithmetic Instructions][map-link-43]
    - [2.11.4. Type Conversion Instructions][map-link-44]
    - [2.11.5. Object Creation and Manipulation][map-link-45]
    - [2.11.6. Operand Stack Management Instructions][map-link-46]
    - [2.11.7. Control Transfer Instructions][map-link-47]
    - [2.11.8. Method Invocation and Return
      Instructions][map-link-48]
    - [2.11.9. Throwing Exceptions][map-link-49]
    - [2.11.10. Synchronization][map-link-50]
  - [2.12. Class Libraries][map-link-51]
  - [2.13. Public Design, Private Implementation][map-link-52]
- [3. Compiling for the Java Virtual Machine][map-link-53]
  - [3.1. Format of Examples][map-link-54]
  - [3.2. Use of Constants, Local Variables, and Control
    Constructs][map-link-55]
  - [3.3. Arithmetic][map-link-56]
  - [3.4. Accessing the Run-Time Constant Pool][map-link-57]
  - [3.5. More Control Examples][map-link-58]
  - [3.6. Receiving Arguments][map-link-59]
  - [3.7. Invoking Methods][map-link-60]
  - [3.8. Working with Class Instances][map-link-61]
  - [3.9. Arrays][map-link-62]
  - [3.10. Compiling Switches][map-link-63]
  - [3.11. Operations on the Operand Stack][map-link-64]
  - [3.12. Throwing and Handling Exceptions][map-link-65]
  - [3.13. Compiling `finally`][map-link-66]
  - [3.14. Synchronization][map-link-67]
  - [3.15. Annotations][map-link-68]
  - [3.16. Modules][map-link-69]
- [4. The `class` File Format][map-link-70]
  - [4.1. The `ClassFile` Structure][map-link-71]
  - [4.2. Names][map-link-72]
    - [4.2.1. Binary Class and Interface Names][map-link-73]
    - [4.2.2. Unqualified Names][map-link-74]
    - [4.2.3. Module and Package Names][map-link-75]
  - [4.3. Descriptors][map-link-76]
    - [4.3.1. Grammar Notation][map-link-77]
    - [4.3.2. Field Descriptors][map-link-78]
    - [4.3.3. Method Descriptors][map-link-79]
  - [4.4. The Constant Pool][map-link-80]
    - [4.4.1. The `CONSTANT_Class_info` Structure][map-link-81]
    - [4.4.2. The `CONSTANT_Fieldref_info`, `CONSTANT_Methodref_info`, and
      `CONSTANT_InterfaceMethodref_info` Structures][map-link-82]
    - [4.4.3. The `CONSTANT_String_info` Structure][map-link-83]
    - [4.4.4. The `CONSTANT_Integer_info` and `CONSTANT_Float_info`
      Structures][map-link-84]
    - [4.4.5. The `CONSTANT_Long_info` and `CONSTANT_Double_info`
      Structures][map-link-85]
    - [4.4.6. The `CONSTANT_NameAndType_info` Structure][map-link-86]
    - [4.4.7. The `CONSTANT_Utf8_info` Structure][map-link-87]
    - [4.4.8. The `CONSTANT_MethodHandle_info` Structure][map-link-88]
    - [4.4.9. The `CONSTANT_MethodType_info` Structure][map-link-89]
    - [4.4.10. The `CONSTANT_Dynamic_info` and `CONSTANT_InvokeDynamic_info`
      Structures][map-link-90]
    - [4.4.11. The `CONSTANT_Module_info` Structure][map-link-91]
    - [4.4.12. The `CONSTANT_Package_info` Structure][map-link-92]
  - [4.5. Fields][map-link-93]
  - [4.6. Methods][map-link-94]
  - [4.7. Attributes][map-link-95]
    - [4.7.1. Defining and Naming New Attributes][map-link-96]
    - [4.7.2. The `ConstantValue` Attribute][map-link-97]
    - [4.7.3. The `Code` Attribute][map-link-98]
    - [4.7.4. The `StackMapTable` Attribute][map-link-99]
    - [4.7.5. The `Exceptions` Attribute][map-link-100]
    - [4.7.6. The `InnerClasses` Attribute][map-link-101]
    - [4.7.7. The `EnclosingMethod` Attribute][map-link-102]
    - [4.7.8. The `Synthetic` Attribute][map-link-103]
    - [4.7.9. The `Signature` Attribute][map-link-104]
      - [4.7.9.1. Signatures][map-link-105]
    - [4.7.10. The `SourceFile` Attribute][map-link-106]
    - [4.7.11. The `SourceDebugExtension` Attribute][map-link-107]
    - [4.7.12. The `LineNumberTable` Attribute][map-link-108]
    - [4.7.13. The `LocalVariableTable` Attribute][map-link-109]
    - [4.7.14. The `LocalVariableTypeTable` Attribute][map-link-110]
    - [4.7.15. The `Deprecated` Attribute][map-link-111]
    - [4.7.16. The `RuntimeVisibleAnnotations` Attribute][map-link-112]
      - [4.7.16.1. The `element_value` structure][map-link-113]
    - [4.7.17. The `RuntimeInvisibleAnnotations` Attribute][map-link-114]
    - [4.7.18. The `RuntimeVisibleParameterAnnotations`
      Attribute][map-link-115]
    - [4.7.19. The `RuntimeInvisibleParameterAnnotations`
      Attribute][map-link-116]
    - [4.7.20. The `RuntimeVisibleTypeAnnotations` Attribute][map-link-117]
      - [4.7.20.1. The `target_info` union][map-link-118]
      - [4.7.20.2. The `type_path` structure][map-link-119]
    - [4.7.21. The `RuntimeInvisibleTypeAnnotations` Attribute][map-link-120]
    - [4.7.22. The `AnnotationDefault` Attribute][map-link-121]
    - [4.7.23. The `BootstrapMethods` Attribute][map-link-122]
    - [4.7.24. The `MethodParameters` Attribute][map-link-123]
    - [4.7.25. The `Module` Attribute][map-link-124]
    - [4.7.26. The `ModulePackages` Attribute][map-link-125]
    - [4.7.27. The `ModuleMainClass` Attribute][map-link-126]
    - [4.7.28. The `NestHost` Attribute][map-link-127]
    - [4.7.29. The `NestMembers` Attribute][map-link-128]
    - [4.7.30. The `Record` Attribute][map-link-129]
    - [4.7.31. The `PermittedSubclasses` Attribute][map-link-130]
  - [4.8. Format Checking][map-link-131]
  - [4.9. Constraints on Java Virtual Machine Code][map-link-132]
    - [4.9.1. Static Constraints][map-link-133]
    - [4.9.2. Structural Constraints][map-link-134]
  - [4.10. Verification of `class` Files][map-link-135]
    - [4.10.1. Verification by Type Checking][map-link-136]
      - [4.10.1.1. Accessors for Java Virtual Machine
        Artifacts][map-link-137]
      - [4.10.1.2. Verification Type System][map-link-138]
      - [4.10.1.3. Instruction Representation][map-link-139]
      - [4.10.1.4. Stack Map Frames and Type Transitions][map-link-140]
      - [4.10.1.5. Type Checking Methods][map-link-141]
      - [4.10.1.6. Type Checking Code Streams][map-link-142]
      - [4.10.1.7. Type Checking Load and Store Instructions][map-link-143]
      - [4.10.1.8. Type Checking for `protected` Members][map-link-144]
      - [4.10.1.9. Type Checking Instructions][map-link-145]
        - [*aaload*][map-link-146]
        - [*aastore*][map-link-147]
        - [*aconst_null*][map-link-148]
        - [*aload*, *aload\_\<n\>*][map-link-149]
        - [*anewarray*][map-link-150]
        - [*areturn*][map-link-151]
        - [*arraylength*][map-link-152]
        - [*astore*, *astore\_\<n\>*][map-link-153]
        - [*athrow*][map-link-154]
        - [*baload*][map-link-155]
        - [*bastore*][map-link-156]
        - [*bipush*][map-link-157]
        - [*caload*][map-link-158]
        - [*castore*][map-link-159]
        - [*checkcast*][map-link-160]
        - [*d2f*, *d2i*, *d2l*][map-link-161]
        - [*dadd*][map-link-162]
        - [*daload*][map-link-163]
        - [*dastore*][map-link-164]
        - [*dcmp\<op\>*][map-link-165]
        - [*dconst\_\<d\>*][map-link-166]
        - [*ddiv*][map-link-167]
        - [*dload*, *dload\_\<n\>*][map-link-168]
        - [*dmul*][map-link-169]
        - [*dneg*][map-link-170]
        - [*drem*][map-link-171]
        - [*dreturn*][map-link-172]
        - [*dstore*, *dstore\_\<n\>*][map-link-173]
        - [*dsub*][map-link-174]
        - [*dup*][map-link-175]
        - [*dup_x1*][map-link-176]
        - [*dup_x2*][map-link-177]
        - [*dup2*][map-link-178]
        - [*dup2_x1*][map-link-179]
        - [*dup2_x2*][map-link-180]
        - [*f2d*, *f2i*, *f2l*][map-link-181]
        - [*fadd*][map-link-182]
        - [*faload*][map-link-183]
        - [*fastore*][map-link-184]
        - [*fcmp\<op\>*][map-link-185]
        - [*fconst\_\<f\>*][map-link-186]
        - [*fdiv*][map-link-187]
        - [*fload*, *fload\_\<n\>*][map-link-188]
        - [*fmul*][map-link-189]
        - [*fneg*][map-link-190]
        - [*frem*][map-link-191]
        - [*freturn*][map-link-192]
        - [*fstore*, *fstore\_\<n\>*][map-link-193]
        - [*fsub*][map-link-194]
        - [*getfield*][map-link-195]
        - [*getstatic*][map-link-196]
        - [*goto*, *goto_w*][map-link-197]
        - [*i2b*, *i2c*, *i2d*, *i2f*, *i2l*, *i2s*][map-link-198]
        - [*iadd*][map-link-199]
        - [*iaload*][map-link-200]
        - [*iand*][map-link-201]
        - [*iastore*][map-link-202]
        - [*iconst\_\<i\>*][map-link-203]
        - [*idiv*][map-link-204]
        - [*if_acmp\<cond\>*][map-link-205]
        - [*if_icmp\<cond\>*][map-link-206]
        - [*if\<cond\>*][map-link-207]
        - [*ifnonnull*, *ifnull*][map-link-208]
        - [*iinc*][map-link-209]
        - [*iload*, *iload\_\<n\>*][map-link-210]
        - [*imul*][map-link-211]
        - [*ineg*][map-link-212]
        - [*instanceof*][map-link-213]
        - [*invokedynamic*][map-link-214]
        - [*invokeinterface*][map-link-215]
        - [*invokespecial*][map-link-216]
        - [*invokestatic*][map-link-217]
        - [*invokevirtual*][map-link-218]
        - [*ior*, *irem*][map-link-219]
        - [*ireturn*][map-link-220]
        - [*ishl*, *ishr*, *iushr*][map-link-221]
        - [*istore*, *istore\_\<n\>*][map-link-222]
        - [*isub*, *ixor*][map-link-223]
        - [*l2d*, *l2f*, *l2i*][map-link-224]
        - [*ladd*][map-link-225]
        - [*laload*][map-link-226]
        - [*land*][map-link-227]
        - [*lastore*][map-link-228]
        - [*lcmp*][map-link-229]
        - [*lconst\_\<l\>*][map-link-230]
        - [*ldc*, *ldc_w*, *ldc2_w*][map-link-231]
        - [*ldiv*][map-link-232]
        - [*lload*, *lload\_\<n\>*][map-link-233]
        - [*lmul*][map-link-234]
        - [*lneg*][map-link-235]
        - [*lookupswitch*][map-link-236]
        - [*lor*, *lrem*][map-link-237]
        - [*lreturn*][map-link-238]
        - [*lshl*, *lshr*, *lushr*][map-link-239]
        - [*lstore*, *lstore\_\<n\>*][map-link-240]
        - [*lsub*, *lxor*][map-link-241]
        - [*monitorenter*, *monitorexit*][map-link-242]
        - [*multianewarray*][map-link-243]
        - [*new*][map-link-244]
        - [*newarray*][map-link-245]
        - [*nop*][map-link-246]
        - [*pop*, *pop2*][map-link-247]
        - [*putfield*][map-link-248]
        - [*putstatic*][map-link-249]
        - [*return*][map-link-250]
        - [*saload*][map-link-251]
        - [*sastore*][map-link-252]
        - [*sipush*][map-link-253]
        - [*swap*][map-link-254]
        - [*tableswitch*][map-link-255]
        - [*wide*][map-link-256]
    - [4.10.2. Verification by Type Inference][map-link-257]
      - [4.10.2.1. The Process of Verification by Type
        Inference][map-link-258]
      - [4.10.2.2. The Bytecode Verifier][map-link-259]
      - [4.10.2.3. Values of Types `long` and `double`][map-link-260]
      - [4.10.2.4. Instance Initialization Methods and Newly Created
        Objects][map-link-261]
      - [4.10.2.5. Exceptions and `finally`][map-link-262]
  - [4.11. Limitations of the Java Virtual Machine][map-link-263]
- [5. Loading, Linking, and Initializing][map-link-264]
  - [5.1. The Run-Time Constant Pool][map-link-265]
  - [5.2. Java Virtual Machine Startup][map-link-266]
  - [5.3. Creation and Loading][map-link-267]
    - [5.3.1. Loading Using the Bootstrap Class
      Loader][map-link-268]
    - [5.3.2. Loading Using a User-defined Class
      Loader][map-link-269]
    - [5.3.3. Creating Array Classes][map-link-270]
    - [5.3.4. Loading Constraints][map-link-271]
    - [5.3.5. Deriving a Class from a `class` File
      Representation][map-link-272]
    - [5.3.6. Modules and Layers][map-link-273]
  - [5.4. Linking][map-link-274]
    - [5.4.1. Verification][map-link-275]
    - [5.4.2. Preparation][map-link-276]
    - [5.4.3. Resolution][map-link-277]
      - [5.4.3.1. Class and Interface Resolution][map-link-278]
      - [5.4.3.2. Field Resolution][map-link-279]
      - [5.4.3.3. Method Resolution][map-link-280]
      - [5.4.3.4. Interface Method Resolution][map-link-281]
      - [5.4.3.5. Method Type and Method Handle
        Resolution][map-link-282]
      - [5.4.3.6. Dynamically-Computed Constant and Call Site
        Resolution][map-link-283]
    - [5.4.4. Access Control][map-link-284]
    - [5.4.5. Method Overriding][map-link-285]
    - [5.4.6. Method Selection][map-link-286]
  - [5.5. Initialization][map-link-287]
  - [5.6. Binding Native Method Implementations][map-link-288]
  - [5.7. Java Virtual Machine Termination][map-link-289]
- [6. The Java Virtual Machine Instruction Set][map-link-290]
  - [6.1. Assumptions: The Meaning of "Must"][map-link-291]
  - [6.2. Reserved Opcodes][map-link-292]
  - [6.3. Virtual Machine Errors][map-link-293]
  - [6.4. Format of Instruction Descriptions][map-link-294]
    - [mnemonic][map-link-295]
  - [6.5. Instructions][map-link-296]
    - [*aaload*][map-link-297]
    - [*aastore*][map-link-298]
    - [*aconst_null*][map-link-299]
    - [*aload*][map-link-300]
    - [*aload\_\<n\>*][map-link-301]
    - [*anewarray*][map-link-302]
    - [*areturn*][map-link-303]
    - [*arraylength*][map-link-304]
    - [*astore*][map-link-305]
    - [*astore\_\<n\>*][map-link-306]
    - [*athrow*][map-link-307]
    - [*baload*][map-link-308]
    - [*bastore*][map-link-309]
    - [*bipush*][map-link-310]
    - [*caload*][map-link-311]
    - [*castore*][map-link-312]
    - [*checkcast*][map-link-313]
    - [*d2f*][map-link-314]
    - [*d2i*][map-link-315]
    - [*d2l*][map-link-316]
    - [*dadd*][map-link-317]
    - [*daload*][map-link-318]
    - [*dastore*][map-link-319]
    - [*dcmp\<op\>*][map-link-320]
    - [*dconst\_\<d\>*][map-link-321]
    - [*ddiv*][map-link-322]
    - [*dload*][map-link-323]
    - [*dload\_\<n\>*][map-link-324]
    - [*dmul*][map-link-325]
    - [*dneg*][map-link-326]
    - [*drem*][map-link-327]
    - [*dreturn*][map-link-328]
    - [*dstore*][map-link-329]
    - [*dstore\_\<n\>*][map-link-330]
    - [*dsub*][map-link-331]
    - [*dup*][map-link-332]
    - [*dup_x1*][map-link-333]
    - [*dup_x2*][map-link-334]
    - [*dup2*][map-link-335]
    - [*dup2_x1*][map-link-336]
    - [*dup2_x2*][map-link-337]
    - [*f2d*][map-link-338]
    - [*f2i*][map-link-339]
    - [*f2l*][map-link-340]
    - [*fadd*][map-link-341]
    - [*faload*][map-link-342]
    - [*fastore*][map-link-343]
    - [*fcmp\<op\>*][map-link-344]
    - [*fconst\_\<f\>*][map-link-345]
    - [*fdiv*][map-link-346]
    - [*fload*][map-link-347]
    - [*fload\_\<n\>*][map-link-348]
    - [*fmul*][map-link-349]
    - [*fneg*][map-link-350]
    - [*frem*][map-link-351]
    - [*freturn*][map-link-352]
    - [*fstore*][map-link-353]
    - [*fstore\_\<n\>*][map-link-354]
    - [*fsub*][map-link-355]
    - [*getfield*][map-link-356]
    - [*getstatic*][map-link-357]
    - [*goto*][map-link-358]
    - [*goto_w*][map-link-359]
    - [*i2b*][map-link-360]
    - [*i2c*][map-link-361]
    - [*i2d*][map-link-362]
    - [*i2f*][map-link-363]
    - [*i2l*][map-link-364]
    - [*i2s*][map-link-365]
    - [*iadd*][map-link-366]
    - [*iaload*][map-link-367]
    - [*iand*][map-link-368]
    - [*iastore*][map-link-369]
    - [*iconst\_\<i\>*][map-link-370]
    - [*idiv*][map-link-371]
    - [*if_acmp\<cond\>*][map-link-372]
    - [*if_icmp\<cond\>*][map-link-373]
    - [*if\<cond\>*][map-link-374]
    - [*ifnonnull*][map-link-375]
    - [*ifnull*][map-link-376]
    - [*iinc*][map-link-377]
    - [*iload*][map-link-378]
    - [*iload\_\<n\>*][map-link-379]
    - [*imul*][map-link-380]
    - [*ineg*][map-link-381]
    - [*instanceof*][map-link-382]
    - [*invokedynamic*][map-link-383]
    - [*invokeinterface*][map-link-384]
    - [*invokespecial*][map-link-385]
    - [*invokestatic*][map-link-386]
    - [*invokevirtual*][map-link-387]
    - [*ior*][map-link-388]
    - [*irem*][map-link-389]
    - [*ireturn*][map-link-390]
    - [*ishl*][map-link-391]
    - [*ishr*][map-link-392]
    - [*istore*][map-link-393]
    - [*istore\_\<n\>*][map-link-394]
    - [*isub*][map-link-395]
    - [*iushr*][map-link-396]
    - [*ixor*][map-link-397]
    - [*jsr*][map-link-398]
    - [*jsr_w*][map-link-399]
    - [*l2d*][map-link-400]
    - [*l2f*][map-link-401]
    - [*l2i*][map-link-402]
    - [*ladd*][map-link-403]
    - [*laload*][map-link-404]
    - [*land*][map-link-405]
    - [*lastore*][map-link-406]
    - [*lcmp*][map-link-407]
    - [*lconst\_\<l\>*][map-link-408]
    - [*ldc*][map-link-409]
    - [*ldc_w*][map-link-410]
    - [*ldc2_w*][map-link-411]
    - [*ldiv*][map-link-412]
    - [*lload*][map-link-413]
    - [*lload\_\<n\>*][map-link-414]
    - [*lmul*][map-link-415]
    - [*lneg*][map-link-416]
    - [*lookupswitch*][map-link-417]
    - [*lor*][map-link-418]
    - [*lrem*][map-link-419]
    - [*lreturn*][map-link-420]
    - [*lshl*][map-link-421]
    - [*lshr*][map-link-422]
    - [*lstore*][map-link-423]
    - [*lstore\_\<n\>*][map-link-424]
    - [*lsub*][map-link-425]
    - [*lushr*][map-link-426]
    - [*lxor*][map-link-427]
    - [*monitorenter*][map-link-428]
    - [*monitorexit*][map-link-429]
    - [*multianewarray*][map-link-430]
    - [*new*][map-link-431]
    - [*newarray*][map-link-432]
    - [*nop*][map-link-433]
    - [*pop*][map-link-434]
    - [*pop2*][map-link-435]
    - [*putfield*][map-link-436]
    - [*putstatic*][map-link-437]
    - [*ret*][map-link-438]
    - [*return*][map-link-439]
    - [*saload*][map-link-440]
    - [*sastore*][map-link-441]
    - [*sipush*][map-link-442]
    - [*swap*][map-link-443]
    - [*tableswitch*][map-link-444]
    - [*wide*][map-link-445]
- [7. Opcode Mnemonics by Opcode][map-link-446]
- [A. Limited License Grant][map-link-447]

## References

- [Oracle Java SE 25 edition][map-link-448].
- [Original legal notice][map-link-449].
- [Original limited license][map-link-450].

[map-link-1]: https://docs.oracle.com/javase/specs/jvms/se25/html/index.html
[map-link-2]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html
[map-link-3]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.1
[map-link-4]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.2
[map-link-5]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.3
[map-link-6]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.4
[map-link-7]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.5
[map-link-8]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.5.1
[map-link-9]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.5.2
[map-link-10]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-1.html#jvms-1.6
[map-link-11]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html
[map-link-12]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.1
[map-link-13]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.2
[map-link-14]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.3
[map-link-15]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.3.1
[map-link-16]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.3.2
[map-link-17]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.3.3
[map-link-18]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.3.4
[map-link-19]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.4
[map-link-20]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5
[map-link-21]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.1
[map-link-22]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.2
[map-link-23]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.3
[map-link-24]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.4
[map-link-25]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.5
[map-link-26]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.6
[map-link-27]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6
[map-link-28]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.1
[map-link-29]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.2
[map-link-30]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.3
[map-link-31]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.4
[map-link-32]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6.5
[map-link-33]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.7
[map-link-34]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.8
[map-link-35]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.9
[map-link-36]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.9.1
[map-link-37]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.9.2
[map-link-38]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.9.3
[map-link-39]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.10
[map-link-40]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11
[map-link-41]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.1
[map-link-42]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.2
[map-link-43]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.3
[map-link-44]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.4
[map-link-45]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.5
[map-link-46]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.6
[map-link-47]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.7
[map-link-48]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.8
[map-link-49]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.9
[map-link-50]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11.10
[map-link-51]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.12
[map-link-52]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.13
[map-link-53]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html
[map-link-54]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.1
[map-link-55]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.2
[map-link-56]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.3
[map-link-57]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.4
[map-link-58]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.5
[map-link-59]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.6
[map-link-60]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.7
[map-link-61]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.8
[map-link-62]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.9
[map-link-63]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.10
[map-link-64]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.11
[map-link-65]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.12
[map-link-66]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.13
[map-link-67]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.14
[map-link-68]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.15
[map-link-69]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.16
[map-link-70]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html
[map-link-71]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.1
[map-link-72]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.2
[map-link-73]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.2.1
[map-link-74]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.2.2
[map-link-75]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.2.3
[map-link-76]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3
[map-link-77]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3.1
[map-link-78]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3.2
[map-link-79]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.3.3
[map-link-80]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4
[map-link-81]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.1
[map-link-82]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.2
[map-link-83]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.3
[map-link-84]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.4
[map-link-85]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.5
[map-link-86]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.6
[map-link-87]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.7
[map-link-88]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.8
[map-link-89]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.9
[map-link-90]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.10
[map-link-91]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.11
[map-link-92]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.4.12
[map-link-93]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.5
[map-link-94]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.6
[map-link-95]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7
[map-link-96]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.1
[map-link-97]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.2
[map-link-98]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.3
[map-link-99]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.4
[map-link-100]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.5
[map-link-101]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.6
[map-link-102]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.7
[map-link-103]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.8
[map-link-104]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.9
[map-link-105]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.9.1
[map-link-106]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.10
[map-link-107]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.11
[map-link-108]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.12
[map-link-109]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.13
[map-link-110]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.14
[map-link-111]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.15
[map-link-112]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.16
[map-link-113]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.16.1
[map-link-114]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.17
[map-link-115]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.18
[map-link-116]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.19
[map-link-117]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.20
[map-link-118]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.20.1
[map-link-119]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.20.2
[map-link-120]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.21
[map-link-121]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.22
[map-link-122]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.23
[map-link-123]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.24
[map-link-124]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.25
[map-link-125]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.26
[map-link-126]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.27
[map-link-127]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.28
[map-link-128]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.29
[map-link-129]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.30
[map-link-130]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.31
[map-link-131]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.8
[map-link-132]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.9
[map-link-133]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.9.1
[map-link-134]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.9.2
[map-link-135]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10
[map-link-136]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1
[map-link-137]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.1
[map-link-138]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.2
[map-link-139]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.3
[map-link-140]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.4
[map-link-141]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.5
[map-link-142]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.6
[map-link-143]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.7
[map-link-144]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.8
[map-link-145]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9
[map-link-146]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.aaload
[map-link-147]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.aastore
[map-link-148]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.aconst_null
[map-link-149]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.aload
[map-link-150]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.anewarray
[map-link-151]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.areturn
[map-link-152]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.arraylength
[map-link-153]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.astore
[map-link-154]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.athrow
[map-link-155]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.baload
[map-link-156]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.bastore
[map-link-157]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.bipush
[map-link-158]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.caload
[map-link-159]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.castore
[map-link-160]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.checkcast
[map-link-161]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.d2f
[map-link-162]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dadd
[map-link-163]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.daload
[map-link-164]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dastore
[map-link-165]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dcmp_op
[map-link-166]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dconst_d
[map-link-167]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ddiv
[map-link-168]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dload
[map-link-169]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dmul
[map-link-170]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dneg
[map-link-171]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.drem
[map-link-172]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dreturn
[map-link-173]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dstore
[map-link-174]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dsub
[map-link-175]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dup
[map-link-176]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dup_x1
[map-link-177]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dup_x2
[map-link-178]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dup2
[map-link-179]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dup2_x1
[map-link-180]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.dup2_x2
[map-link-181]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.f2d
[map-link-182]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fadd
[map-link-183]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.faload
[map-link-184]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fastore
[map-link-185]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fcmp_op
[map-link-186]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fconst_f
[map-link-187]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fdiv
[map-link-188]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fload
[map-link-189]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fmul
[map-link-190]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fneg
[map-link-191]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.frem
[map-link-192]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.freturn
[map-link-193]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fstore
[map-link-194]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.fsub
[map-link-195]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.getfield
[map-link-196]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.getstatic
[map-link-197]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.goto
[map-link-198]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.i2b
[map-link-199]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.iadd
[map-link-200]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.iaload
[map-link-201]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.iand
[map-link-202]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.iastore
[map-link-203]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.iconst_i
[map-link-204]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.idiv
[map-link-205]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.if_acmp_cond
[map-link-206]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.if_icmp_cond
[map-link-207]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.if_cond
[map-link-208]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ifnonnull
[map-link-209]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.iinc
[map-link-210]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.iload
[map-link-211]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.imul
[map-link-212]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ineg
[map-link-213]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.instanceof
[map-link-214]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.invokedynamic
[map-link-215]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.invokeinterface
[map-link-216]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.invokespecial
[map-link-217]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.invokestatic
[map-link-218]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.invokevirtual
[map-link-219]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ior
[map-link-220]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ireturn
[map-link-221]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ishl
[map-link-222]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.istore
[map-link-223]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.isub
[map-link-224]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.l2d
[map-link-225]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ladd
[map-link-226]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.laload
[map-link-227]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.land
[map-link-228]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lastore
[map-link-229]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lcmp
[map-link-230]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lconst_l
[map-link-231]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ldc
[map-link-232]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.ldiv
[map-link-233]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lload
[map-link-234]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lmul
[map-link-235]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lneg
[map-link-236]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lookupswitch
[map-link-237]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lor
[map-link-238]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lreturn
[map-link-239]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lshl
[map-link-240]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lstore
[map-link-241]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.lsub
[map-link-242]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.monitorenter
[map-link-243]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.multianewarray
[map-link-244]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.new
[map-link-245]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.newarray
[map-link-246]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.nop
[map-link-247]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.pop
[map-link-248]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.putfield
[map-link-249]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.putstatic
[map-link-250]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.return
[map-link-251]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.saload
[map-link-252]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.sastore
[map-link-253]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.sipush
[map-link-254]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.swap
[map-link-255]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.tableswitch
[map-link-256]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.1.9.wide
[map-link-257]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.2
[map-link-258]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.2.1
[map-link-259]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.2.2
[map-link-260]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.2.3
[map-link-261]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.2.4
[map-link-262]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.2.5
[map-link-263]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.11
[map-link-264]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html
[map-link-265]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.1
[map-link-266]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.2
[map-link-267]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3
[map-link-268]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.1
[map-link-269]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.2
[map-link-270]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.3
[map-link-271]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.4
[map-link-272]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.5
[map-link-273]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.6
[map-link-274]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4
[map-link-275]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.1
[map-link-276]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.2
[map-link-277]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3
[map-link-278]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3.1
[map-link-279]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3.2
[map-link-280]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3.3
[map-link-281]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3.4
[map-link-282]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3.5
[map-link-283]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.3.6
[map-link-284]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.4
[map-link-285]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.5
[map-link-286]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.6
[map-link-287]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.5
[map-link-288]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.6
[map-link-289]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.7
[map-link-290]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html
[map-link-291]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.1
[map-link-292]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.2
[map-link-293]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.3
[map-link-294]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.4
[map-link-295]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.4-mnemonic
[map-link-296]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5
[map-link-297]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.aaload
[map-link-298]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.aastore
[map-link-299]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.aconst_null
[map-link-300]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.aload
[map-link-301]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.aload_n
[map-link-302]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.anewarray
[map-link-303]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.areturn
[map-link-304]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.arraylength
[map-link-305]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.astore
[map-link-306]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.astore_n
[map-link-307]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.athrow
[map-link-308]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.baload
[map-link-309]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.bastore
[map-link-310]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.bipush
[map-link-311]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.caload
[map-link-312]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.castore
[map-link-313]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.checkcast
[map-link-314]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.d2f
[map-link-315]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.d2i
[map-link-316]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.d2l
[map-link-317]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dadd
[map-link-318]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.daload
[map-link-319]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dastore
[map-link-320]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dcmp_op
[map-link-321]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dconst_d
[map-link-322]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ddiv
[map-link-323]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dload
[map-link-324]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dload_n
[map-link-325]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dmul
[map-link-326]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dneg
[map-link-327]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.drem
[map-link-328]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dreturn
[map-link-329]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dstore
[map-link-330]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dstore_n
[map-link-331]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dsub
[map-link-332]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dup
[map-link-333]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dup_x1
[map-link-334]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dup_x2
[map-link-335]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dup2
[map-link-336]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dup2_x1
[map-link-337]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.dup2_x2
[map-link-338]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.f2d
[map-link-339]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.f2i
[map-link-340]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.f2l
[map-link-341]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fadd
[map-link-342]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.faload
[map-link-343]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fastore
[map-link-344]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fcmp_op
[map-link-345]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fconst_f
[map-link-346]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fdiv
[map-link-347]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fload
[map-link-348]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fload_n
[map-link-349]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fmul
[map-link-350]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fneg
[map-link-351]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.frem
[map-link-352]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.freturn
[map-link-353]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fstore
[map-link-354]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fstore_n
[map-link-355]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.fsub
[map-link-356]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.getfield
[map-link-357]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.getstatic
[map-link-358]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.goto
[map-link-359]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.goto_w
[map-link-360]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.i2b
[map-link-361]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.i2c
[map-link-362]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.i2d
[map-link-363]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.i2f
[map-link-364]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.i2l
[map-link-365]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.i2s
[map-link-366]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iadd
[map-link-367]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iaload
[map-link-368]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iand
[map-link-369]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iastore
[map-link-370]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iconst_i
[map-link-371]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.idiv
[map-link-372]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.if_acmp_cond
[map-link-373]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.if_icmp_cond
[map-link-374]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.if_cond
[map-link-375]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ifnonnull
[map-link-376]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ifnull
[map-link-377]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iinc
[map-link-378]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iload
[map-link-379]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iload_n
[map-link-380]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.imul
[map-link-381]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ineg
[map-link-382]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.instanceof
[map-link-383]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.invokedynamic
[map-link-384]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.invokeinterface
[map-link-385]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.invokespecial
[map-link-386]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.invokestatic
[map-link-387]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.invokevirtual
[map-link-388]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ior
[map-link-389]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.irem
[map-link-390]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ireturn
[map-link-391]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ishl
[map-link-392]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ishr
[map-link-393]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.istore
[map-link-394]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.istore_n
[map-link-395]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.isub
[map-link-396]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.iushr
[map-link-397]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ixor
[map-link-398]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.jsr
[map-link-399]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.jsr_w
[map-link-400]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.l2d
[map-link-401]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.l2f
[map-link-402]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.l2i
[map-link-403]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ladd
[map-link-404]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.laload
[map-link-405]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.land
[map-link-406]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lastore
[map-link-407]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lcmp
[map-link-408]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lconst_l
[map-link-409]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ldc
[map-link-410]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ldc_w
[map-link-411]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ldc2_w
[map-link-412]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ldiv
[map-link-413]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lload
[map-link-414]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lload_n
[map-link-415]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lmul
[map-link-416]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lneg
[map-link-417]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lookupswitch
[map-link-418]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lor
[map-link-419]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lrem
[map-link-420]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lreturn
[map-link-421]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lshl
[map-link-422]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lshr
[map-link-423]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lstore
[map-link-424]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lstore_n
[map-link-425]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lsub
[map-link-426]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lushr
[map-link-427]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.lxor
[map-link-428]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.monitorenter
[map-link-429]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.monitorexit
[map-link-430]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.multianewarray
[map-link-431]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.new
[map-link-432]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.newarray
[map-link-433]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.nop
[map-link-434]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.pop
[map-link-435]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.pop2
[map-link-436]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.putfield
[map-link-437]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.putstatic
[map-link-438]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.ret
[map-link-439]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.return
[map-link-440]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.saload
[map-link-441]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.sastore
[map-link-442]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.sipush
[map-link-443]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.swap
[map-link-444]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.tableswitch
[map-link-445]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html#jvms-6.5.wide
[map-link-446]: https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-7.html
[map-link-447]: https://docs.oracle.com/javase/specs/jvms/se25/html/spec-license.html
[map-link-448]: https://docs.oracle.com/javase/specs/jvms/se25/html/index.html
[map-link-449]: https://docs.oracle.com/javase/specs/jvms/se25/html/spec-frontmatter.html
[map-link-450]: https://docs.oracle.com/javase/specs/jvms/se25/html/spec-license.html
