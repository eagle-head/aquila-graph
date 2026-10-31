# Java Language Specification — Java SE 25

[Source map](../README.md)

Edition: Java SE 25. This is a navigation index of the official JLS sections, examples, and appendices.
Every content link opens Oracle; no specification body is reproduced here.

[Official contents][map-link-1]

## Complete contents

- [1. Introduction][map-link-2]
  - [1.1. Organization of the Specification][map-link-3]
  - [1.2. Example Programs][map-link-4]
  - [1.3. Notation][map-link-5]
  - [1.4. Relationship to Predefined Classes and Interfaces][map-link-6]
  - [1.5. Preview Features][map-link-7]
    - [1.5.1. Restrictions on the Use of Preview Features][map-link-8]
    - [1.5.2. Current Preview Language Features][map-link-9]
  - [1.6. Feedback][map-link-10]
  - [1.7. References][map-link-11]
- [2. Grammars][map-link-12]
  - [2.1. Context-Free Grammars][map-link-13]
  - [2.2. The Lexical Grammar][map-link-14]
  - [2.3. The Syntactic Grammar][map-link-15]
  - [2.4. Grammar Notation][map-link-16]
- [3. Lexical Structure][map-link-17]
  - [3.1. Unicode][map-link-18]
  - [3.2. Lexical Translations][map-link-19]
  - [3.3. Unicode Escapes][map-link-20]
  - [3.4. Line Terminators][map-link-21]
  - [3.5. Input Elements and Tokens][map-link-22]
  - [3.6. White Space][map-link-23]
  - [3.7. Comments][map-link-24]
  - [3.8. Identifiers][map-link-25]
  - [3.9. Keywords][map-link-26]
  - [3.10. Literals][map-link-27]
    - [3.10.1. Integer Literals][map-link-28]
    - [3.10.2. Floating-Point Literals][map-link-29]
    - [3.10.3. Boolean Literals][map-link-30]
    - [3.10.4. Character Literals][map-link-31]
    - [3.10.5. String Literals][map-link-32]
    - [3.10.6. Text Blocks][map-link-33]
    - [3.10.7. Escape Sequences][map-link-34]
    - [3.10.8. The Null Literal][map-link-35]
  - [3.11. Separators][map-link-36]
  - [3.12. Operators][map-link-37]
- [4. Types, Values, and Variables][map-link-38]
  - [4.1. The Kinds of Types and Values][map-link-39]
  - [4.2. Primitive Types and Values][map-link-40]
    - [4.2.1. Integral Types and Values][map-link-41]
    - [4.2.2. Integer Operations][map-link-42]
    - [4.2.3. Floating-Point Types and Values][map-link-43]
    - [4.2.4. Floating-Point Operations][map-link-44]
    - [4.2.5. The `boolean` Type and boolean Values][map-link-45]
  - [4.3. Reference Types and Values][map-link-46]
    - [4.3.1. Objects][map-link-47]
    - [4.3.2. The Class `Object`][map-link-48]
    - [4.3.3. The Class `String`][map-link-49]
    - [4.3.4. When Reference Types Are the Same][map-link-50]
  - [4.4. Type Variables][map-link-51]
  - [4.5. Parameterized Types][map-link-52]
    - [4.5.1. Type Arguments of Parameterized Types][map-link-53]
    - [4.5.2. Members and Constructors of Parameterized
      Types][map-link-54]
  - [4.6. Type Erasure][map-link-55]
  - [4.7. Reifiable Types][map-link-56]
  - [4.8. Raw Types][map-link-57]
  - [4.9. Intersection Types][map-link-58]
  - [4.10. Subtyping][map-link-59]
    - [4.10.1. Subtyping among Primitive Types][map-link-60]
    - [4.10.2. Subtyping among Class and Interface Types][map-link-61]
    - [4.10.3. Subtyping among Array Types][map-link-62]
    - [4.10.4. Least Upper Bound][map-link-63]
    - [4.10.5. Type Projections][map-link-64]
  - [4.11. Where Types Are Used][map-link-65]
  - [4.12. Variables][map-link-66]
    - [4.12.1. Variables of Primitive Type][map-link-67]
    - [4.12.2. Variables of Reference Type][map-link-68]
    - [4.12.3. Kinds of Variables][map-link-69]
    - [4.12.4. `final` Variables][map-link-70]
    - [4.12.5. Initial Values of Variables][map-link-71]
    - [4.12.6. Types, Classes, and Interfaces][map-link-72]
- [5. Conversions and Contexts][map-link-73]
  - [5.1. Kinds of Conversion][map-link-74]
    - [5.1.1. Identity Conversion][map-link-75]
    - [5.1.2. Widening Primitive Conversion][map-link-76]
    - [5.1.3. Narrowing Primitive Conversion][map-link-77]
    - [5.1.4. Widening and Narrowing Primitive Conversion][map-link-78]
    - [5.1.5. Widening Reference Conversion][map-link-79]
    - [5.1.6. Narrowing Reference Conversion][map-link-80]
      - [5.1.6.1. Allowed Narrowing Reference Conversion][map-link-81]
      - [5.1.6.2. Checked and Unchecked Narrowing Reference
        Conversions][map-link-82]
      - [5.1.6.3. Narrowing Reference Conversions at Run
        Time][map-link-83]
    - [5.1.7. Boxing Conversion][map-link-84]
    - [5.1.8. Unboxing Conversion][map-link-85]
    - [5.1.9. Unchecked Conversion][map-link-86]
    - [5.1.10. Capture Conversion][map-link-87]
    - [5.1.11. String Conversion][map-link-88]
    - [5.1.12. Forbidden Conversions][map-link-89]
  - [5.2. Assignment Contexts][map-link-90]
  - [5.3. Invocation Contexts][map-link-91]
  - [5.4. String Contexts][map-link-92]
  - [5.5. Casting Contexts][map-link-93]
  - [5.6. Numeric Contexts][map-link-94]
  - [5.7. Testing Contexts][map-link-95]
- [6. Names][map-link-96]
  - [6.1. Declarations][map-link-97]
  - [6.2. Names and Identifiers][map-link-98]
  - [6.3. Scope of a Declaration][map-link-99]
    - [6.3.1. Scope for Pattern Variables in Expressions][map-link-100]
      - [6.3.1.1. Conditional-And Operator `&&`][map-link-101]
      - [6.3.1.2. Conditional-Or Operator `||`][map-link-102]
      - [6.3.1.3. Logical Complement Operator `!`][map-link-103]
      - [6.3.1.4. Conditional Operator `? :`][map-link-104]
      - [6.3.1.5. Pattern Match Operator `instanceof`][map-link-105]
      - [6.3.1.6. `switch` Expressions][map-link-106]
      - [6.3.1.7. Parenthesized Expressions][map-link-107]
    - [6.3.2. Scope for Pattern Variables in Statements][map-link-108]
      - [6.3.2.1. Blocks][map-link-109]
      - [6.3.2.2. `if` Statements][map-link-110]
      - [6.3.2.3. `while` Statements][map-link-111]
      - [6.3.2.4. `do` Statements][map-link-112]
      - [6.3.2.5. `for` Statements][map-link-113]
      - [6.3.2.6. `switch` Statements][map-link-114]
      - [6.3.2.7. Labeled Statements][map-link-115]
    - [6.3.3. Scope for Pattern Variables in `case` Labels][map-link-116]
  - [6.4. Shadowing and Obscuring][map-link-117]
    - [6.4.1. Shadowing][map-link-118]
    - [6.4.2. Obscuring][map-link-119]
  - [6.5. Determining the Meaning of a Name][map-link-120]
    - [6.5.1. Syntactic Classification of a Name According to Context][map-link-121]
    - [6.5.2. Reclassification of Contextually Ambiguous Names][map-link-122]
    - [6.5.3. Meaning of Module Names and Package Names][map-link-123]
      - [6.5.3.1. Simple Package Names][map-link-124]
      - [6.5.3.2. Qualified Package Names][map-link-125]
    - [6.5.4. Meaning of *PackageOrTypeNames*][map-link-126]
      - [6.5.4.1. Simple *PackageOrTypeNames*][map-link-127]
      - [6.5.4.2. Qualified *PackageOrTypeNames*][map-link-128]
    - [6.5.5. Meaning of Type Names][map-link-129]
      - [6.5.5.1. Simple Type Names][map-link-130]
      - [6.5.5.2. Qualified Type Names][map-link-131]
    - [6.5.6. Meaning of Expression Names][map-link-132]
      - [6.5.6.1. Simple Expression Names][map-link-133]
      - [6.5.6.2. Qualified Expression Names][map-link-134]
    - [6.5.7. Meaning of Method Names][map-link-135]
      - [6.5.7.1. Simple Method Names][map-link-136]
  - [6.6. Access Control][map-link-137]
    - [6.6.1. Determining Accessibility][map-link-138]
    - [6.6.2. Details on `protected` Access][map-link-139]
      - [6.6.2.1. Access to a `protected` Member][map-link-140]
      - [6.6.2.2. Access to a `protected` Constructor][map-link-141]
  - [6.7. Fully Qualified Names and Canonical Names][map-link-142]
- [7. Packages and Modules][map-link-143]
  - [7.1. Package Members][map-link-144]
  - [7.2. Host Support for Modules and Packages][map-link-145]
  - [7.3. Compilation Units][map-link-146]
  - [7.4. Package Declarations][map-link-147]
    - [7.4.1. Named Packages][map-link-148]
    - [7.4.2. Unnamed Packages][map-link-149]
    - [7.4.3. Package Observability and Visibility][map-link-150]
  - [7.5. Import Declarations][map-link-151]
    - [7.5.1. Single-Type-Import Declarations][map-link-152]
    - [7.5.2. Type-Import-on-Demand Declarations][map-link-153]
    - [7.5.3. Single-Static-Import Declarations][map-link-154]
    - [7.5.4. Static-Import-on-Demand Declarations][map-link-155]
    - [7.5.5. Single-Module-Import Declarations][map-link-156]
  - [7.6. Top Level Class and Interface Declarations][map-link-157]
  - [7.7. Module Declarations][map-link-158]
    - [7.7.1. Dependences][map-link-159]
    - [7.7.2. Exported and Opened Packages][map-link-160]
    - [7.7.3. Service Consumption][map-link-161]
    - [7.7.4. Service Provision][map-link-162]
    - [7.7.5. Unnamed Modules][map-link-163]
    - [7.7.6. Observability of a Module][map-link-164]
- [8. Classes][map-link-165]
  - [8.1. Class Declarations][map-link-166]
    - [8.1.1. Class Modifiers][map-link-167]
      - [8.1.1.1. `abstract` Classes][map-link-168]
      - [8.1.1.2. `sealed`, `non-sealed`, and `final` Classes][map-link-169]
      - [8.1.1.3. `strictfp` Classes][map-link-170]
      - [8.1.1.4. `static` Classes][map-link-171]
    - [8.1.2. Generic Classes and Type Parameters][map-link-172]
    - [8.1.3. Inner Classes and Enclosing Instances][map-link-173]
    - [8.1.4. Superclasses and Subclasses][map-link-174]
    - [8.1.5. Superinterfaces][map-link-175]
    - [8.1.6. Permitted Direct Subclasses][map-link-176]
    - [8.1.7. Class Body and Member Declarations][map-link-177]
    - [8.1.8. Implicitly Declared Classes][map-link-178]
  - [8.2. Class Members][map-link-179]
  - [8.3. Field Declarations][map-link-180]
    - [8.3.1. Field Modifiers][map-link-181]
      - [8.3.1.1. `static` Fields][map-link-182]
      - [8.3.1.2. `final` Fields][map-link-183]
      - [8.3.1.3. `transient` Fields][map-link-184]
      - [8.3.1.4. `volatile` Fields][map-link-185]
    - [8.3.2. Field Initialization][map-link-186]
    - [8.3.3. Restrictions on Field References in Initializers][map-link-187]
  - [8.4. Method Declarations][map-link-188]
    - [8.4.1. Formal Parameters][map-link-189]
    - [8.4.2. Method Signature][map-link-190]
    - [8.4.3. Method Modifiers][map-link-191]
      - [8.4.3.1. `abstract` Methods][map-link-192]
      - [8.4.3.2. `static` Methods][map-link-193]
      - [8.4.3.3. `final` Methods][map-link-194]
      - [8.4.3.4. `native` Methods][map-link-195]
      - [8.4.3.5. `strictfp` Methods][map-link-196]
      - [8.4.3.6. `synchronized` Methods][map-link-197]
    - [8.4.4. Generic Methods][map-link-198]
    - [8.4.5. Method Result][map-link-199]
    - [8.4.6. Method Throws][map-link-200]
    - [8.4.7. Method Body][map-link-201]
    - [8.4.8. Inheritance, Overriding, and Hiding][map-link-202]
      - [8.4.8.1. Overriding (by Instance Methods)][map-link-203]
      - [8.4.8.2. Hiding (by Class Methods)][map-link-204]
      - [8.4.8.3. Requirements in Overriding and Hiding][map-link-205]
      - [8.4.8.4. Inheriting Methods with Override-Equivalent Signatures][map-link-206]
    - [8.4.9. Overloading][map-link-207]
  - [8.5. Member Class and Interface Declarations][map-link-208]
  - [8.6. Instance Initializers][map-link-209]
  - [8.7. Static Initializers][map-link-210]
  - [8.8. Constructor Declarations][map-link-211]
    - [8.8.1. Formal Parameters][map-link-212]
    - [8.8.2. Constructor Signature][map-link-213]
    - [8.8.3. Constructor Modifiers][map-link-214]
    - [8.8.4. Generic Constructors][map-link-215]
    - [8.8.5. Constructor Throws][map-link-216]
    - [8.8.6. The Type of a Constructor][map-link-217]
    - [8.8.7. Constructor Body][map-link-218]
      - [8.8.7.1. Constructor Invocations][map-link-219]
    - [8.8.8. Constructor Overloading][map-link-220]
    - [8.8.9. Default Constructor][map-link-221]
    - [8.8.10. Preventing Instantiation of a Class][map-link-222]
  - [8.9. Enum Classes][map-link-223]
    - [8.9.1. Enum Constants][map-link-224]
    - [8.9.2. Enum Body Declarations][map-link-225]
    - [8.9.3. Enum Members][map-link-226]
  - [8.10. Record Classes][map-link-227]
    - [8.10.1. Record Components][map-link-228]
    - [8.10.2. Record Body Declarations][map-link-229]
    - [8.10.3. Record Members][map-link-230]
    - [8.10.4. Record Constructor Declarations][map-link-231]
      - [8.10.4.1. Normal Canonical Constructors][map-link-232]
      - [8.10.4.2. Compact Canonical Constructors][map-link-233]
- [9. Interfaces][map-link-234]
  - [9.1. Interface Declarations][map-link-235]
    - [9.1.1. Interface Modifiers][map-link-236]
      - [9.1.1.1. `abstract` Interfaces][map-link-237]
      - [9.1.1.2. `strictfp` Interfaces][map-link-238]
      - [9.1.1.3. `static` Interfaces][map-link-239]
      - [9.1.1.4. `sealed` and `non-sealed` Interfaces][map-link-240]
    - [9.1.2. Generic Interfaces and Type Parameters][map-link-241]
    - [9.1.3. Superinterfaces and Subinterfaces][map-link-242]
    - [9.1.4. Permitted Direct Subclasses and Subinterfaces][map-link-243]
    - [9.1.5. Interface Body and Member Declarations][map-link-244]
  - [9.2. Interface Members][map-link-245]
  - [9.3. Field (Constant) Declarations][map-link-246]
    - [9.3.1. Initialization of Fields in Interfaces][map-link-247]
  - [9.4. Method Declarations][map-link-248]
    - [9.4.1. Inheritance and Overriding][map-link-249]
      - [9.4.1.1. Overriding (by Instance Methods)][map-link-250]
      - [9.4.1.2. Requirements in Overriding][map-link-251]
      - [9.4.1.3. Inheriting Methods with Override-Equivalent
        Signatures][map-link-252]
    - [9.4.2. Overloading][map-link-253]
    - [9.4.3. Interface Method Body][map-link-254]
  - [9.5. Member Class and Interface Declarations][map-link-255]
  - [9.6. Annotation Interfaces][map-link-256]
    - [9.6.1. Annotation Interface Elements][map-link-257]
    - [9.6.2. Defaults for Annotation Interface Elements][map-link-258]
    - [9.6.3. Repeatable Annotation Interfaces][map-link-259]
    - [9.6.4. Predefined Annotation Interfaces][map-link-260]
      - [9.6.4.1. `@Target`][map-link-261]
      - [9.6.4.2. `@Retention`][map-link-262]
      - [9.6.4.3. `@Inherited`][map-link-263]
      - [9.6.4.4. `@Override`][map-link-264]
      - [9.6.4.5. `@SuppressWarnings`][map-link-265]
      - [9.6.4.6. `@Deprecated`][map-link-266]
      - [9.6.4.7. `@SafeVarargs`][map-link-267]
      - [9.6.4.8. `@Repeatable`][map-link-268]
      - [9.6.4.9. `@FunctionalInterface`][map-link-269]
  - [9.7. Annotations][map-link-270]
    - [9.7.1. Normal Annotations][map-link-271]
    - [9.7.2. Marker Annotations][map-link-272]
    - [9.7.3. Single-Element Annotations][map-link-273]
    - [9.7.4. Where Annotations May Appear][map-link-274]
    - [9.7.5. Multiple Annotations of the Same Interface][map-link-275]
  - [9.8. Functional Interfaces][map-link-276]
  - [9.9. Function Types][map-link-277]
- [10. Arrays][map-link-278]
  - [10.1. Array Types][map-link-279]
  - [10.2. Array Variables][map-link-280]
  - [10.3. Array Creation][map-link-281]
  - [10.4. Array Access][map-link-282]
  - [10.5. Array Store Exception][map-link-283]
  - [10.6. Array Initializers][map-link-284]
  - [10.7. Array Members][map-link-285]
  - [10.8. `Class` Objects for Arrays][map-link-286]
  - [10.9. An Array of Characters Is Not a `String`][map-link-287]
- [11. Exceptions][map-link-288]
  - [11.1. The Kinds and Causes of Exceptions][map-link-289]
    - [11.1.1. The Kinds of Exceptions][map-link-290]
    - [11.1.2. The Causes of Exceptions][map-link-291]
    - [11.1.3. Asynchronous Exceptions][map-link-292]
  - [11.2. Compile-Time Checking of Exceptions][map-link-293]
    - [11.2.1. Exception Analysis of Expressions][map-link-294]
    - [11.2.2. Exception Analysis of Statements and Constructor
      Invocations][map-link-295]
    - [11.2.3. Exception Checking][map-link-296]
  - [11.3. Run-Time Handling of an Exception][map-link-297]
- [12. Execution][map-link-298]
  - [12.1. Java Virtual Machine Startup][map-link-299]
    - [12.1.1. Load the Initial Class or Interface][map-link-300]
    - [12.1.2. Link the Initial Class or Interface: Verify, Prepare, (Optionally)
      Resolve][map-link-301]
    - [12.1.3. Initialize the Initial Class or Interface: Execute
      Initializers][map-link-302]
    - [12.1.4. Invoke a `main` Method][map-link-303]
  - [12.2. Loading of Classes and Interfaces][map-link-304]
    - [12.2.1. The Loading Process][map-link-305]
    - [12.2.2. Class Loader Consistency][map-link-306]
  - [12.3. Linking of Classes and Interfaces][map-link-307]
    - [12.3.1. Verification of the Binary Representation][map-link-308]
    - [12.3.2. Preparation of a Class or Interface][map-link-309]
    - [12.3.3. Resolution of Symbolic References][map-link-310]
  - [12.4. Initialization of Classes and Interfaces][map-link-311]
    - [12.4.1. When Initialization Occurs][map-link-312]
    - [12.4.2. Detailed Initialization Procedure][map-link-313]
  - [12.5. Creation of New Class Instances][map-link-314]
  - [12.6. Finalization of Class Instances][map-link-315]
    - [12.6.1. Implementing Finalization][map-link-316]
    - [12.6.2. Interaction with the Memory Model][map-link-317]
  - [12.7. Unloading of Classes and Interfaces][map-link-318]
  - [12.8. Program Exit][map-link-319]
- [13. Binary Compatibility][map-link-320]
  - [13.1. The Form of a Binary][map-link-321]
  - [13.2. What Binary Compatibility Is and Is Not][map-link-322]
  - [13.3. Evolution of Packages and Modules][map-link-323]
  - [13.4. Evolution of Classes][map-link-324]
    - [13.4.1. `abstract` Classes][map-link-325]
    - [13.4.2. `sealed`, `non-sealed`, and `final` Classes][map-link-326]
      - [13.4.2.1. `sealed` Classes][map-link-327]
      - [13.4.2.2. `non-sealed` Classes][map-link-328]
      - [13.4.2.3. `final` Classes][map-link-329]
    - [13.4.3. `public` Classes][map-link-330]
    - [13.4.4. Superclasses and Superinterfaces][map-link-331]
    - [13.4.5. Class Type Parameters][map-link-332]
    - [13.4.6. Class Body and Member Declarations][map-link-333]
    - [13.4.7. Access to Members and Constructors][map-link-334]
    - [13.4.8. Field Declarations][map-link-335]
    - [13.4.9. `final` Fields and `static` Constant
      Variables][map-link-336]
    - [13.4.10. `static` Fields][map-link-337]
    - [13.4.11. `transient` Fields][map-link-338]
    - [13.4.12. Method and Constructor Declarations][map-link-339]
    - [13.4.13. Method and Constructor Type Parameters][map-link-340]
    - [13.4.14. Method and Constructor Formal Parameters][map-link-341]
    - [13.4.15. Method Result Type][map-link-342]
    - [13.4.16. `abstract` Methods][map-link-343]
    - [13.4.17. `final` Methods][map-link-344]
    - [13.4.18. `native` Methods][map-link-345]
    - [13.4.19. `static` Methods][map-link-346]
    - [13.4.20. `synchronized` Methods][map-link-347]
    - [13.4.21. Method and Constructor Throws][map-link-348]
    - [13.4.22. Method and Constructor Body][map-link-349]
    - [13.4.23. Method and Constructor Overloading][map-link-350]
    - [13.4.24. Method Overriding][map-link-351]
    - [13.4.25. Static Initializers][map-link-352]
    - [13.4.26. Evolution of Enum Classes][map-link-353]
    - [13.4.27. Evolution of Record Classes][map-link-354]
  - [13.5. Evolution of Interfaces][map-link-355]
    - [13.5.1. `public` Interfaces][map-link-356]
    - [13.5.2. `sealed` and `non-sealed` Interfaces][map-link-357]
    - [13.5.3. Superinterfaces][map-link-358]
    - [13.5.4. Interface Members][map-link-359]
    - [13.5.5. Interface Type Parameters][map-link-360]
    - [13.5.6. Field Declarations][map-link-361]
    - [13.5.7. Interface Method Declarations][map-link-362]
    - [13.5.8. Annotation Interfaces][map-link-363]
- [14. Blocks, Statements, and Patterns][map-link-364]
  - [14.1. Normal and Abrupt Completion of Statements][map-link-365]
  - [14.2. Blocks][map-link-366]
  - [14.3. Local Class and Interface Declarations][map-link-367]
  - [14.4. Local Variable Declarations][map-link-368]
    - [14.4.1. Local Variable Declarators and Types][map-link-369]
    - [14.4.2. Local Variable Declaration Statements][map-link-370]
  - [14.5. Statements][map-link-371]
  - [14.6. The Empty Statement][map-link-372]
  - [14.7. Labeled Statements][map-link-373]
  - [14.8. Expression Statements][map-link-374]
  - [14.9. The `if` Statement][map-link-375]
    - [14.9.1. The `if`-`then` Statement][map-link-376]
    - [14.9.2. The `if`-`then`-`else` Statement][map-link-377]
  - [14.10. The `assert` Statement][map-link-378]
  - [14.11. The `switch` Statement][map-link-379]
    - [14.11.1. Switch Blocks][map-link-380]
      - [14.11.1.1. Exhaustive Switch Blocks][map-link-381]
      - [14.11.1.2. Determining which Switch Label Applies at Run
        Time][map-link-382]
    - [14.11.2. The Switch Block of a `switch`
      Statement][map-link-383]
    - [14.11.3. Execution of a `switch` Statement][map-link-384]
  - [14.12. The `while` Statement][map-link-385]
    - [14.12.1. Abrupt Completion of `while`
      Statement][map-link-386]
  - [14.13. The `do` Statement][map-link-387]
    - [14.13.1. Abrupt Completion of `do` Statement][map-link-388]
  - [14.14. The `for` Statement][map-link-389]
    - [14.14.1. The basic `for` Statement][map-link-390]
      - [14.14.1.1. Initialization of `for`
        Statement][map-link-391]
      - [14.14.1.2. Iteration of `for` Statement][map-link-392]
      - [14.14.1.3. Abrupt Completion of `for`
        Statement][map-link-393]
    - [14.14.2. The enhanced `for` statement][map-link-394]
  - [14.15. The `break` Statement][map-link-395]
  - [14.16. The `continue` Statement][map-link-396]
  - [14.17. The `return` Statement][map-link-397]
  - [14.18. The `throw` Statement][map-link-398]
  - [14.19. The `synchronized` Statement][map-link-399]
  - [14.20. The `try` statement][map-link-400]
    - [14.20.1. Execution of `try`-`catch`][map-link-401]
    - [14.20.2. Execution of `try`-`finally` and
      `try`-`catch`-`finally`][map-link-402]
    - [14.20.3. `try`-with-resources][map-link-403]
      - [14.20.3.1. Basic `try`-with-resources][map-link-404]
      - [14.20.3.2. Extended `try`-with-resources][map-link-405]
  - [14.21. The `yield` Statement][map-link-406]
  - [14.22. Unreachable Statements][map-link-407]
  - [14.30. Patterns][map-link-408]
    - [14.30.1. Kinds of Patterns][map-link-409]
    - [14.30.2. Pattern Matching][map-link-410]
    - [14.30.3. Properties of Patterns][map-link-411]
- [15. Expressions][map-link-412]
  - [15.1. Evaluation, Denotation, and Result][map-link-413]
  - [15.2. Forms of Expressions][map-link-414]
  - [15.3. Type of an Expression][map-link-415]
  - [15.4. Floating-point Expressions][map-link-416]
  - [15.5. Expressions and Run-Time Checks][map-link-417]
  - [15.6. Normal and Abrupt Completion of Evaluation][map-link-418]
  - [15.7. Evaluation Order][map-link-419]
    - [15.7.1. Evaluate Left-Hand Operand First][map-link-420]
    - [15.7.2. Evaluate Operands before Operation][map-link-421]
    - [15.7.3. Evaluation Respects Parentheses and Precedence][map-link-422]
    - [15.7.4. Argument Lists are Evaluated Left-to-Right][map-link-423]
    - [15.7.5. Evaluation Order for Other Expressions][map-link-424]
  - [15.8. Primary Expressions][map-link-425]
    - [15.8.1. Lexical Literals][map-link-426]
    - [15.8.2. Class Literals][map-link-427]
    - [15.8.3. `this`][map-link-428]
    - [15.8.4. Qualified `this`][map-link-429]
    - [15.8.5. Parenthesized Expressions][map-link-430]
  - [15.9. Class Instance Creation Expressions][map-link-431]
    - [15.9.1. Determining the Class being Instantiated][map-link-432]
    - [15.9.2. Determining Enclosing Instances][map-link-433]
    - [15.9.3. Choosing the Constructor and its Arguments][map-link-434]
    - [15.9.4. Run-Time Evaluation of Class Instance Creation
      Expressions][map-link-435]
    - [15.9.5. Anonymous Class Declarations][map-link-436]
      - [15.9.5.1. Anonymous Constructors][map-link-437]
  - [15.10. Array Creation and Access Expressions][map-link-438]
    - [15.10.1. Array Creation Expressions][map-link-439]
    - [15.10.2. Run-Time Evaluation of Array Creation Expressions][map-link-440]
    - [15.10.3. Array Access Expressions][map-link-441]
    - [15.10.4. Run-Time Evaluation of Array Access Expressions][map-link-442]
  - [15.11. Field Access Expressions][map-link-443]
    - [15.11.1. Field Access Using a Primary][map-link-444]
    - [15.11.2. Accessing Superclass Members using `super`][map-link-445]
  - [15.12. Method Invocation Expressions][map-link-446]
    - [15.12.1. Compile-Time Step 1: Determine Type to Search][map-link-447]
    - [15.12.2. Compile-Time Step 2: Determine Method Signature][map-link-448]
      - [15.12.2.1. Identify Potentially Applicable Methods][map-link-449]
      - [15.12.2.2. Phase 1: Identify Matching Arity Methods Applicable by Strict
        Invocation][map-link-450]
      - [15.12.2.3. Phase 2: Identify Matching Arity Methods Applicable by Loose
        Invocation][map-link-451]
      - [15.12.2.4. Phase 3: Identify Methods Applicable by Variable Arity
        Invocation][map-link-452]
      - [15.12.2.5. Choosing the Most Specific Method][map-link-453]
      - [15.12.2.6. Method Invocation Type][map-link-454]
    - [15.12.3. Compile-Time Step 3: Is the Chosen Method
      Appropriate?][map-link-455]
    - [15.12.4. Run-Time Evaluation of Method Invocation][map-link-456]
      - [15.12.4.1. Compute Target Reference (If Necessary)][map-link-457]
      - [15.12.4.2. Evaluate Arguments][map-link-458]
      - [15.12.4.3. Check Accessibility of Type and Method][map-link-459]
      - [15.12.4.4. Locate Method to Invoke][map-link-460]
      - [15.12.4.5. Create Frame, Synchronize, Transfer Control][map-link-461]
  - [15.13. Method Reference Expressions][map-link-462]
    - [15.13.1. Compile-Time Declaration of a Method Reference][map-link-463]
    - [15.13.2. Type of a Method Reference][map-link-464]
    - [15.13.3. Run-Time Evaluation of Method References][map-link-465]
  - [15.14. Postfix Expressions][map-link-466]
    - [15.14.1. Expression Names][map-link-467]
    - [15.14.2. Postfix Increment Operator `++`][map-link-468]
    - [15.14.3. Postfix Decrement Operator `--`][map-link-469]
  - [15.15. Unary Operators][map-link-470]
    - [15.15.1. Prefix Increment Operator `++`][map-link-471]
    - [15.15.2. Prefix Decrement Operator `--`][map-link-472]
    - [15.15.3. Unary Plus Operator `+`][map-link-473]
    - [15.15.4. Unary Minus Operator `-`][map-link-474]
    - [15.15.5. Bitwise Complement Operator `~`][map-link-475]
    - [15.15.6. Logical Complement Operator `!`][map-link-476]
  - [15.16. Cast Expressions][map-link-477]
  - [15.17. Multiplicative Operators][map-link-478]
    - [15.17.1. Multiplication Operator `*`][map-link-479]
    - [15.17.2. Division Operator `/`][map-link-480]
    - [15.17.3. Remainder Operator `%`][map-link-481]
  - [15.18. Additive Operators][map-link-482]
    - [15.18.1. String Concatenation Operator `+`][map-link-483]
    - [15.18.2. Additive Operators (`+` and `-`) for Numeric Types][map-link-484]
  - [15.19. Shift Operators][map-link-485]
  - [15.20. Relational Operators][map-link-486]
    - [15.20.1. Numerical Comparison Operators `<`, `<=`, `>`, and
      `>=`][map-link-487]
    - [15.20.2. The `instanceof` Operator][map-link-488]
  - [15.21. Equality Operators][map-link-489]
    - [15.21.1. Numerical Equality Operators `==` and `!=`][map-link-490]
    - [15.21.2. Boolean Equality Operators `==` and `!=`][map-link-491]
    - [15.21.3. Reference Equality Operators `==` and `!=`][map-link-492]
  - [15.22. Bitwise and Logical Operators][map-link-493]
    - [15.22.1. Integer Bitwise Operators `&`, `^`, and `|`][map-link-494]
    - [15.22.2. Boolean Logical Operators `&`, `^`, and `|`][map-link-495]
  - [15.23. Conditional-And Operator `&&`][map-link-496]
  - [15.24. Conditional-Or Operator `||`][map-link-497]
  - [15.25. Conditional Operator `? :`][map-link-498]
    - [15.25.1. Boolean Conditional Expressions][map-link-499]
    - [15.25.2. Numeric Conditional Expressions][map-link-500]
    - [15.25.3. Reference Conditional Expressions][map-link-501]
  - [15.26. Assignment Operators][map-link-502]
    - [15.26.1. Simple Assignment Operator `=`][map-link-503]
    - [15.26.2. Compound Assignment Operators][map-link-504]
  - [15.27. Lambda Expressions][map-link-505]
    - [15.27.1. Lambda Parameters][map-link-506]
    - [15.27.2. Lambda Body][map-link-507]
    - [15.27.3. Type of a Lambda Expression][map-link-508]
    - [15.27.4. Run-Time Evaluation of Lambda Expressions][map-link-509]
  - [15.28. `switch` Expressions][map-link-510]
    - [15.28.1. The Switch Block of a `switch` Expression][map-link-511]
    - [15.28.2. Run-Time Evaluation of `switch` Expressions][map-link-512]
  - [15.29. Constant Expressions][map-link-513]
- [16. Definite Assignment][map-link-514]
  - [16.1. Definite Assignment and Expressions][map-link-515]
    - [16.1.1. Boolean Constant Expressions][map-link-516]
    - [16.1.2. Conditional-And Operator `&&`][map-link-517]
    - [16.1.3. Conditional-Or Operator `||`][map-link-518]
    - [16.1.4. Logical Complement Operator `!`][map-link-519]
    - [16.1.5. Conditional Operator `? :`][map-link-520]
    - [16.1.6. `switch` Expressions][map-link-521]
    - [16.1.7. Other Expressions of Type `boolean`][map-link-522]
    - [16.1.8. Assignment Expressions][map-link-523]
    - [16.1.9. Operators `++` and `--`][map-link-524]
    - [16.1.10. Other Expressions][map-link-525]
  - [16.2. Definite Assignment and Statements][map-link-526]
    - [16.2.1. Empty Statements][map-link-527]
    - [16.2.2. Blocks][map-link-528]
    - [16.2.3. Local Class and Interface Declarations][map-link-529]
    - [16.2.4. Local Variable Declaration Statements][map-link-530]
    - [16.2.5. Labeled Statements][map-link-531]
    - [16.2.6. Expression Statements][map-link-532]
    - [16.2.7. `if` Statements][map-link-533]
    - [16.2.8. `assert` Statements][map-link-534]
    - [16.2.9. `switch` Statements][map-link-535]
    - [16.2.10. `while` Statements][map-link-536]
    - [16.2.11. `do` Statements][map-link-537]
    - [16.2.12. `for` Statements][map-link-538]
      - [16.2.12.1. Initialization Part of `for` Statement][map-link-539]
      - [16.2.12.2. Incrementation Part of `for` Statement][map-link-540]
    - [16.2.13. `break`, `yield`, `continue`, `return`, and `throw`
      Statements][map-link-541]
    - [16.2.14. `synchronized` Statements][map-link-542]
    - [16.2.15. `try` Statements][map-link-543]
  - [16.3. Definite Assignment and Parameters][map-link-544]
  - [16.4. Definite Assignment and Array Initializers][map-link-545]
  - [16.5. Definite Assignment and Enum Constants][map-link-546]
  - [16.6. Definite Assignment and Anonymous Classes][map-link-547]
  - [16.7. Definite Assignment and Member Classes and
    Interfaces][map-link-548]
  - [16.8. Definite Assignment and Static Initializers][map-link-549]
  - [16.9. Definite Assignment, Constructors, and Instance
    Initializers][map-link-550]
- [17. Threads and Locks][map-link-551]
  - [17.1. Synchronization][map-link-552]
  - [17.2. Wait Sets and Notification][map-link-553]
    - [17.2.1. Wait][map-link-554]
    - [17.2.2. Notification][map-link-555]
    - [17.2.3. Interruptions][map-link-556]
    - [17.2.4. Interactions of Waits, Notification, and
      Interruption][map-link-557]
  - [17.3. Sleep and Yield][map-link-558]
  - [17.4. Memory Model][map-link-559]
    - [17.4.1. Shared Variables][map-link-560]
    - [17.4.2. Actions][map-link-561]
    - [17.4.3. Programs and Program Order][map-link-562]
    - [17.4.4. Synchronization Order][map-link-563]
    - [17.4.5. Happens-before Order][map-link-564]
    - [17.4.6. Executions][map-link-565]
    - [17.4.7. Well-Formed Executions][map-link-566]
    - [17.4.8. Executions and Causality Requirements][map-link-567]
    - [17.4.9. Observable Behavior and Nonterminating Executions][map-link-568]
  - [17.5. `final` Field Semantics][map-link-569]
    - [17.5.1. Semantics of `final` Fields][map-link-570]
    - [17.5.2. Reading `final` Fields During Construction][map-link-571]
    - [17.5.3. Subsequent Modification of `final` Fields][map-link-572]
    - [17.5.4. Write-Protected Fields][map-link-573]
  - [17.6. Word Tearing][map-link-574]
  - [17.7. Non-Atomic Treatment of `double` and `long`][map-link-575]
- [18. Type Inference][map-link-576]
  - [18.1. Concepts and Notation][map-link-577]
    - [18.1.1. Inference Variables][map-link-578]
    - [18.1.2. Constraint Formulas][map-link-579]
    - [18.1.3. Bounds][map-link-580]
  - [18.2. Reduction][map-link-581]
    - [18.2.1. Expression Compatibility Constraints][map-link-582]
    - [18.2.2. Type Compatibility Constraints][map-link-583]
    - [18.2.3. Subtyping Constraints][map-link-584]
    - [18.2.4. Type Equality Constraints][map-link-585]
    - [18.2.5. Checked Exception Constraints][map-link-586]
  - [18.3. Incorporation][map-link-587]
    - [18.3.1. Complementary Pairs of Bounds][map-link-588]
    - [18.3.2. Bounds Involving Capture Conversion][map-link-589]
  - [18.4. Resolution][map-link-590]
  - [18.5. Uses of Inference][map-link-591]
    - [18.5.1. Invocation Applicability Inference][map-link-592]
    - [18.5.2. Invocation Type Inference][map-link-593]
      - [18.5.2.1. Poly Method Invocation Compatibility][map-link-594]
      - [18.5.2.2. Additional Argument Constraints][map-link-595]
    - [18.5.3. Functional Interface Parameterization Inference][map-link-596]
    - [18.5.4. More Specific Method Inference][map-link-597]
    - [18.5.5. Record Pattern Type Inference][map-link-598]
- [19. Syntax][map-link-599]
- [A. Limited License Grant][map-link-600]

## Examples

- 3.10.5-1. [String Literals][map-link-601]
- 3.10.6-1. [Text Blocks][map-link-602]
- 3.10.6-2. [Escape sequences in text blocks][map-link-603]
- 3.10.6-3. [Order of transformations on text block content][map-link-604]
- 3.10.6-4. [Text blocks evaluate to `String`][map-link-605]
- 4.2.2-1. [Integer Operations][map-link-606]
- 4.2.4-1. [Floating-point Operations][map-link-607]
- 4.3.1-1. [Object Creation][map-link-608]
- 4.3.1-2. [Primitive and Reference Identity][map-link-609]
- 4.4-1. [Members of a Type Variable][map-link-610]
- 4.5.1-1. [Unbounded Wildcards][map-link-611]
- 4.5.1-2. [Bounded Wildcards][map-link-612]
- 4.8-1. [Raw Types][map-link-613]
- 4.8-2. [Raw Types and Inheritance][map-link-614]
- 4.11-1. [Usage of a Type][map-link-615]
- 4.12.3-1. [Different Kinds of Variables][map-link-616]
- 4.12.4-1. [Final Variables][map-link-617]
- 4.12.5-1. [Initial Values of Variables][map-link-618]
- 4.12.6-1. [Type of a Variable versus Class of an Object][map-link-619]
- 5.0-1. [Conversions at Compile Time and Run Time][map-link-620]
- 5.0-2. [Conversions In Various Contexts][map-link-621]
- 5.1.2-1. [Widening Primitive Conversion][map-link-622]
- 5.1.3-1. [Narrowing Primitive Conversion][map-link-623]
- 5.1.3-2. [Narrowing Primitive Conversions that lose
  information][map-link-624]
- 5.2-1. [Assignment for Primitive Types][map-link-625]
- 5.2-2. [Assignment for Reference Types][map-link-626]
- 5.2-3. [Assignment for Array Types][map-link-627]
- 5.5-1. [Casting for Reference Types][map-link-628]
- 5.5-2. [Casting for Array Types][map-link-629]
- 5.5-3. [Casting Incompatible Types at Run Time][map-link-630]
- 5.6-1. [Unary Numeric Promotion][map-link-631]
- 5.6-2. [Binary Numeric Promotion][map-link-632]
- 6.1-1. [Unique Package Names][map-link-633]
- 6.1-2. [Unique Module Names][map-link-634]
- 6.1-3. [Descriptive Class Names][map-link-635]
- 6.1-4. [Conventional Type Variable Names][map-link-636]
- 6.3-1. [Scope of Class Declarations][map-link-637]
- 6.3-2. [Scope of Local Variable Declarations][map-link-638]
- 6.4-1. [Attempted Shadowing Of A Local Variable][map-link-639]
- 6.4.1-1. [Shadowing of a Field Declaration by a Local Variable Declaration][map-link-640]
- 6.4.1-2. [Shadowing of a Type Declaration by Another Type Declaration][map-link-641]
- 6.5.2-1. [Reclassification of Contextually Ambiguous Names][map-link-642]
- 6.5.5.1-1. [References to Type Parameters][map-link-643]
- 6.5.5.2-1. [Qualified Type Names][map-link-644]
- 6.5.6.1-1. [Simple Expression Names][map-link-645]
- 6.5.6.1-2. [References to Instance Variables][map-link-646]
- 6.5.6.1-3. [References to Local Variables and Formal Parameters][map-link-647]
- 6.5.6.2-1. [Qualified Expression Names][map-link-648]
- 6.5.6.2-2. [Qualifying an Expression with a Type Name][map-link-649]
- 6.5.7.1-1. [Simple Method Names][map-link-650]
- 6.6-1. [Access Control][map-link-651]
- 6.6-2. [Access to `public` Fields, Methods, and Constructors][map-link-652]
- 6.6-3. [Access to `public` and Non-`public` Classes][map-link-653]
- 6.6-4. [Access to Fields, Methods, and Constructors with Package Access][map-link-654]
- 6.6-5. [Access to `private` Fields, Methods, and Constructors][map-link-655]
- 6.6.2-1. [Access to `protected` Fields, Methods, and Constructors][map-link-656]
- 6.7-1. [Fully Qualified Names][map-link-657]
- 6.7-2. [Fully Qualified Names v. Canonical Name][map-link-658]
- 7.4.2-1. [Unnamed Package][map-link-659]
- 7.5.1-1. [Single-Type-Import][map-link-660]
- 7.5.1-2. [Duplicate Class Declarations][map-link-661]
- 7.5.1-3. [No Import of a Subpackage][map-link-662]
- 7.5.1-4. [Importing a Type Name that is also a Package Name][map-link-663]
- 7.5.2-1. [Type-Import-on-Demand][map-link-664]
- 7.5.5-1. [Single-Module-Import in Ordinary Compilation Units][map-link-665]
- 7.5.5-2. [Single-Module-Import in Modular Compilation Units][map-link-666]
- 7.5.5-3. [Ambiguous Imports][map-link-667]
- 7.6-1. [Conflicting Top Level Class and Interface Declarations][map-link-668]
- 7.6-2. [Scope of Top Level Classes and Interfaces][map-link-669]
- 7.6-3. [Fully Qualified Names][map-link-670]
- 7.1.1-1. [Resolution of `requires` `transitive` directives][map-link-671]
- 8.1.1.1-1. [Abstract Class Declaration][map-link-672]
- 8.1.1.1-2. [Abstract Class Declaration that Prohibits Subclasses][map-link-673]
- 8.1.2-1. [Mutually Recursive Type Variable Bounds][map-link-674]
- 8.1.2-2. [Nested Generic Classes][map-link-675]
- 8.1.3-1. [Inner Class Declarations and Static Members][map-link-676]
- 8.1.3-2. [Inner Class Declarations][map-link-677]
- 8.1.4-1. [Direct Superclasses and Subclasses][map-link-678]
- 8.1.4-2. [Superclasses and Subclasses][map-link-679]
- 8.1.4-3. [Class Depends on Itself][map-link-680]
- 8.1.5-1. [Illegal Superinterfaces][map-link-681]
- 8.1.5-2. [Superinterfaces][map-link-682]
- 8.1.5-3. [Illegal Multiple Inheritance of an Interface][map-link-683]
- 8.1.5-4. [Implementing Methods of a Superinterface][map-link-684]
- 8.2-1. [Use of Class Members][map-link-685]
- 8.2-2. [Inheritance of Class Members with Package Access][map-link-686]
- 8.2-3. [Inheritance of `public` and `protected` Class Members][map-link-687]
- 8.2-4. [Inheritance of `private` Class Members][map-link-688]
- 8.2-5. [Accessing Members of Inaccessible Classes][map-link-689]
- 8.3-1. [Multiply Inherited Fields][map-link-690]
- 8.3-2. [Re-inheritance of Fields][map-link-691]
- 8.3.1.1-1. [`static` Fields][map-link-692]
- 8.3.1.1-2. [Hiding of Class Variables][map-link-693]
- 8.3.1.1-3. [Hiding of Instance Variables][map-link-694]
- 8.3.1.3-1. [Persistence of `transient` Fields][map-link-695]
- 8.3.1.4-1. [`volatile` Fields][map-link-696]
- 8.3.2-1. [Field Initialization][map-link-697]
- 8.3.2-2. [Forward Reference to a Class Variable][map-link-698]
- 8.3.3-1. [Restrictions on Field References][map-link-699]
- 8.4.2-1. [Override-Equivalent Signatures][map-link-700]
- 8.4.3.1-1. [Abstract/Abstract Method Overriding][map-link-701]
- 8.4.3.1-2. [Abstract/Non-Abstract Overriding][map-link-702]
- 8.4.3.6-1. [`synchronized` Monitors][map-link-703]
- 8.4.3.6-2. [`synchronized` Methods][map-link-704]
- 8.4.6-1. [Type Variables as Thrown Exception Types][map-link-705]
- 8.4.8-1. [Inheritance][map-link-706]
- 8.4.8.1-1. [Overriding][map-link-707]
- 8.4.8.1-2. [Overriding][map-link-708]
- 8.4.8.2-1. [Invocation of Hidden Class Methods][map-link-709]
- 8.4.8.3-1. [Covariant Return Types][map-link-710]
- 8.4.8.3-2. [Unchecked Warning from Return Type][map-link-711]
- 8.4.8.3-3. [Incorrect Overriding because of `throws`][map-link-712]
- 8.4.8.3-4. [Erasure Affects Overriding][map-link-713]
- 8.4.8.4-1. [Inheritance of override-equivalent methods][map-link-714]
- 8.4.9-1. [Overloading][map-link-715]
- 8.4.9-2. [Overloading, Overriding, and Hiding][map-link-716]
- 8.8-1. [Constructor Declarations][map-link-717]
- 8.8.7-1. [Constructor Bodies][map-link-718]
- 8.8.7.1-1. [Restrictions on Constructor Invocations][map-link-719]
- 8.8.7.1-2. [Qualified Superclass Constructor Invocation][map-link-720]
- 8.8.9-1. [Default Constructors][map-link-721]
- 8.8.9-2. [Accessibility of Constructors v. Classes][map-link-722]
- 8.8.10-1. [Preventing Instantiation via Constructor Accessibility][map-link-723]
- 8.9.2-1. [Enum Body Declarations][map-link-724]
- 8.9.2-2. [Restriction On Enum Constant Self-Reference][map-link-725]
- 8.9.3-1. [Iterating Over Enum Constants With An Enhanced `for` Loop][map-link-726]
- 8.9.3-2. [Switching Over Enum Constants][map-link-727]
- 8.9.3-3. [Enum Constants with Class Bodies][map-link-728]
- 8.9.3-4. [Multiple Enum Classes][map-link-729]
- 9.3-1. [Ambiguous Inherited Fields][map-link-730]
- 9.3-2. [Multiply Inherited Fields][map-link-731]
- 9.3.1-1. [Forward Reference to a Field][map-link-732]
- 9.4.2-1. [Overloading an `abstract` Method Declaration][map-link-733]
- 9.6.1-1. [Annotation Interface Declaration][map-link-734]
- 9.6.1-2. [Marker Annotation Interface Declaration][map-link-735]
- 9.6.1-3. [Single-Element Annotation Interface Declarations][map-link-736]
- 9.6.2-1. [Annotation Interface Declaration With Default Values][map-link-737]
- 9.6.3-1. [Ill-formed Containing Annotation Interface][map-link-738]
- 9.6.3-2. [Restricting Where Annotations May Repeat][map-link-739]
- 9.6.3-3. [A Repeatable Containing Annotation Interface][map-link-740]
- 9.7.1-1. [Normal Annotations][map-link-741]
- 9.7.2-1. [Marker Annotations][map-link-742]
- 9.7.3-1. [Single-Element Annotations][map-link-743]
- 9.8-1. [Functional Interfaces][map-link-744]
- 9.8-2. [Functional Interfaces and Erasure][map-link-745]
- 9.8-3. [Generic Functional Interfaces][map-link-746]
- 9.9-1. [Function Types][map-link-747]
- 9.9-2. [Generic Function Types][map-link-748]
- 10.2-1. [Declarations of Array Variables][map-link-749]
- 10.2-2. [Array Variables and Array Types][map-link-750]
- 10.4-1. [Array Access][map-link-751]
- 10.5-1. [`ArrayStoreException`][map-link-752]
- 10.6-1. [Array Initializers][map-link-753]
- 10.7-1. [Arrays Are Cloneable][map-link-754]
- 10.7-2. [Shared Subarrays After A Clone][map-link-755]
- 10.8-1. [`Class` Object Of Array][map-link-756]
- 10.8-2. [Array `Class` Objects Are Shared][map-link-757]
- 11.2.3-1. [Catching Checked Exceptions][map-link-758]
- 11.3-1. [Throwing and Catching Exceptions][map-link-759]
- 12.4.1-1. [Superclasses Are Initialized Before Subclasses][map-link-760]
- 12.4.1-2. [Only The Class That Declares `static` Field Is Initialized][map-link-761]
- 12.4.1-3. [Interface Initialization Does Not Initialize Superinterfaces][map-link-762]
- 12.5-1. [Evaluation of Instance Creation][map-link-763]
- 12.5-2. [Dynamic Dispatch During Instance Creation][map-link-764]
- 12.5-3. [Initialization of Fields in the Prologue][map-link-765]
- 13.4.4-1. [Changing A Superclass][map-link-766]
- 13.4.4-2. [Introducing a Superclass][map-link-767]
- 13.4.6-1. [Changing A Class Body][map-link-768]
- 13.4.6-2. [Changing A Superclass][map-link-769]
- 13.4.7-1. [Changing Accessibility][map-link-770]
- 13.4.8-1. [Adding A Field Declaration][map-link-771]
- 13.4.9-1. [Changing A Variable To Be `final`][map-link-772]
- 13.4.16-1. [Changing A Method To Be `abstract`][map-link-773]
- 13.4.17-1. [Changing A Method To Be `final`][map-link-774]
- 13.4.23-1. [Adding An Overloaded Method][map-link-775]
- 13.5.4-1. [Deleting An Interface Member][map-link-776]
- 13.5.7-1. [Adding A Default Method][map-link-777]
- 14.3-1. [Local Class Declarations][map-link-778]
- 14.4-1. [Local Variables Declared With `var`][map-link-779]
- 14.4.1-1. [Type of Local Variables Declared With `var`][map-link-780]
- 14.7-1. [Labels and Identifiers][map-link-781]
- 14.11.3-1. [Fall-Through in the `switch` Statement][map-link-782]
- 14.13-1. [The `do` Statement][map-link-783]
- 14.14-1. [Enhanced `for` And Arrays][map-link-784]
- 14.14-2. [Enhanced `for` And Unboxing Conversion][map-link-785]
- 14.15-1. [The `break` Statement][map-link-786]
- 14.16-1. [The `continue` Statement][map-link-787]
- 14.19-1. [The `synchronized` Statement][map-link-788]
- 14.20.1-1. [Catching An Exception][map-link-789]
- 14.20.2-1. [Handling An Uncaught Exception With
  `finally`][map-link-790]
- 14.21-1. [The `yield` Statement][map-link-791]
- 14.22-1. [Conditional Compilation][map-link-792]
- 15.7.1-1. [Left-Hand Operand Is Evaluated First][map-link-793]
- 15.7.1-2. [Implicit Left-Hand Operand In Operator Of Compound
  Assigment][map-link-794]
- 15.7.1-3. [Abrupt Completion of Evaluation of the Left-Hand Operand][map-link-795]
- 15.7.2-1. [Evaluation of Operands Before Operation][map-link-796]
- 15.7.4-1. [Evaluation Order At Method Invocation][map-link-797]
- 15.7.4-2. [Abrupt Completion of Argument Expression][map-link-798]
- 15.8.3-1. [The `this` Expression][map-link-799]
- 15.9.4-1. [Evaluation Order and Out-Of-Memory Detection][map-link-800]
- 15.10.2-1. [Array Creation Evaluation][map-link-801]
- 15.10.2-2. [Multi-Dimensional Array Creation][map-link-802]
- 15.10.2-3. [`OutOfMemoryError` and Dimension Expression Evaluation][map-link-803]
- 15.10.4-1. [Array Reference Is Evaluated First][map-link-804]
- 15.10.4-2. [Abrupt Completion of Array Reference Evaluation][map-link-805]
- 15.10.4-3. [`null` Array Reference][map-link-806]
- 15.11.1-1. [Static Binding for Field Access][map-link-807]
- 15.11.1-2. [Receiver Variable Is Irrelevant For `static` Field Access][map-link-808]
- 15.11.2-1. [The `super` Expression][map-link-809]
- 15.12.2-1. [Method Applicability][map-link-810]
- 15.12.2-2. [Return Type Not Considered During Method Selection][map-link-811]
- 15.12.2-3. [Choosing The Most Specific Method][map-link-812]
- 15.12.4.1-1. [Target References and `static` Methods][map-link-813]
- 15.12.4.1-2. [Evaluation Order During Method Invocation][map-link-814]
- 15.12.4.4-1. [Overriding and Method Invocation][map-link-815]
- 15.12.4.4-2. [Method Invocation Using `super`][map-link-816]
- 15.12.4.5-1. [Invoked Method Signature Has Different Erasure Than Compile-Time Method
  Signature][map-link-817]
- 15.17.3-1. [Integer Remainder Operator][map-link-818]
- 15.17.3-2. [Floating-Point Remainder Operator][map-link-819]
- 15.18.1-1. [String Concatenation][map-link-820]
- 15.18.1-2. [String Concatenation and Conditionals][map-link-821]
- 15.20.2-1. [The Type Comparison Operator][map-link-822]
- 15.26.1-1. [Simple Assignment To An Array Component][map-link-823]
- 15.26.2-1. [Compound Assignment To An Array Component][map-link-824]
- 15.26.2-2. [Value Of Left-Hand Side Of Compound Assignment Is Saved Before Evaluation Of
  Right-Hand Side][map-link-825]
- 15.29-1. [Constant Expressions][map-link-826]
- 16-1. [Definite Assignment Considers Structure of Statements and
  Expressions][map-link-827]
- 16-2. [Definite Assignment Does Not Consider Values of
  Expressions][map-link-828]
- 16-3. [Definite Unassignment][map-link-829]
- 17.4-1. [Incorrectly Synchronized Programs May Exhibit Surprising
  Behavior][map-link-830]
- 17.4.5-1. [Happens-before Consistency][map-link-831]
- 17.4.8-1. [Happens-before Consistency Is Not Sufficient][map-link-832]
- 17.5-1. [`final` Fields In The Java Memory Model][map-link-833]
- 17.5-2. [`final` Fields For Security][map-link-834]
- 17.5.3-1. [Aggressive Optimization of `final` Fields][map-link-835]
- 17.6-1. [Detection of Word Tearing][map-link-836]
- 18.5.5-1. [Record Pattern Type Inference][map-link-837]

## References

- [Oracle Java SE 25 edition][map-link-838].
- [Original legal notice][map-link-839].
- [Original limited license][map-link-840].

[map-link-1]: https://docs.oracle.com/javase/specs/jls/se25/html/index.html
[map-link-2]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html
[map-link-3]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.1
[map-link-4]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.2
[map-link-5]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.3
[map-link-6]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.4
[map-link-7]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.5
[map-link-8]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.5.1
[map-link-9]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.5.2
[map-link-10]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.6
[map-link-11]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-1.html#jls-1.7
[map-link-12]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-2.html
[map-link-13]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-2.html#jls-2.1
[map-link-14]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-2.html#jls-2.2
[map-link-15]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-2.html#jls-2.3
[map-link-16]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-2.html#jls-2.4
[map-link-17]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html
[map-link-18]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.1
[map-link-19]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.2
[map-link-20]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.3
[map-link-21]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.4
[map-link-22]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.5
[map-link-23]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.6
[map-link-24]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.7
[map-link-25]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.8
[map-link-26]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.9
[map-link-27]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10
[map-link-28]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.1
[map-link-29]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.2
[map-link-30]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.3
[map-link-31]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.4
[map-link-32]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.5
[map-link-33]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.6
[map-link-34]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.7
[map-link-35]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.8
[map-link-36]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.11
[map-link-37]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.12
[map-link-38]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html
[map-link-39]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.1
[map-link-40]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2
[map-link-41]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.1
[map-link-42]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.2
[map-link-43]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.3
[map-link-44]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.4
[map-link-45]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.5
[map-link-46]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.3
[map-link-47]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.3.1
[map-link-48]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.3.2
[map-link-49]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.3.3
[map-link-50]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.3.4
[map-link-51]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.4
[map-link-52]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5
[map-link-53]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.1
[map-link-54]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.5.2
[map-link-55]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.6
[map-link-56]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.7
[map-link-57]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.8
[map-link-58]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.9
[map-link-59]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.10
[map-link-60]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.10.1
[map-link-61]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.10.2
[map-link-62]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.10.3
[map-link-63]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.10.4
[map-link-64]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.10.5
[map-link-65]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.11
[map-link-66]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12
[map-link-67]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.1
[map-link-68]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.2
[map-link-69]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.3
[map-link-70]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.4
[map-link-71]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.5
[map-link-72]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.6
[map-link-73]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html
[map-link-74]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1
[map-link-75]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.1
[map-link-76]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.2
[map-link-77]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.3
[map-link-78]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.4
[map-link-79]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.5
[map-link-80]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.6
[map-link-81]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.6.1
[map-link-82]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.6.2
[map-link-83]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.6.3
[map-link-84]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.7
[map-link-85]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.8
[map-link-86]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.9
[map-link-87]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.10
[map-link-88]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.11
[map-link-89]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.1.12
[map-link-90]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.2
[map-link-91]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.3
[map-link-92]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.4
[map-link-93]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.5
[map-link-94]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.6
[map-link-95]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.7
[map-link-96]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html
[map-link-97]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.1
[map-link-98]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.2
[map-link-99]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3
[map-link-100]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1
[map-link-101]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1.1
[map-link-102]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1.2
[map-link-103]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1.3
[map-link-104]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1.4
[map-link-105]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1.5
[map-link-106]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1.6
[map-link-107]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.1.7
[map-link-108]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2
[map-link-109]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2.1
[map-link-110]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2.2
[map-link-111]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2.3
[map-link-112]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2.4
[map-link-113]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2.5
[map-link-114]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2.6
[map-link-115]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.2.7
[map-link-116]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.3.3
[map-link-117]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.4
[map-link-118]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.4.1
[map-link-119]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.4.2
[map-link-120]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5
[map-link-121]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.1
[map-link-122]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.2
[map-link-123]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.3
[map-link-124]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.3.1
[map-link-125]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.3.2
[map-link-126]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.4
[map-link-127]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.4.1
[map-link-128]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.4.2
[map-link-129]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.5
[map-link-130]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.5.1
[map-link-131]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.5.2
[map-link-132]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.6
[map-link-133]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.6.1
[map-link-134]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.6.2
[map-link-135]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.7
[map-link-136]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.5.7.1
[map-link-137]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.6
[map-link-138]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.6.1
[map-link-139]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.6.2
[map-link-140]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.6.2.1
[map-link-141]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.6.2.2
[map-link-142]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#jls-6.7
[map-link-143]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html
[map-link-144]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.1
[map-link-145]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.2
[map-link-146]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.3
[map-link-147]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.4
[map-link-148]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.4.1
[map-link-149]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.4.2
[map-link-150]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.4.3
[map-link-151]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.5
[map-link-152]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.5.1
[map-link-153]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.5.2
[map-link-154]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.5.3
[map-link-155]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.5.4
[map-link-156]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.5.5
[map-link-157]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.6
[map-link-158]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7
[map-link-159]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.1
[map-link-160]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.2
[map-link-161]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.3
[map-link-162]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.4
[map-link-163]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.5
[map-link-164]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#jls-7.7.6
[map-link-165]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html
[map-link-166]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1
[map-link-167]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.1
[map-link-168]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.1.1
[map-link-169]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.1.2
[map-link-170]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.1.3
[map-link-171]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.1.4
[map-link-172]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.2
[map-link-173]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.3
[map-link-174]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.4
[map-link-175]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.5
[map-link-176]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.6
[map-link-177]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.7
[map-link-178]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.1.8
[map-link-179]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.2
[map-link-180]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3
[map-link-181]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.1
[map-link-182]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.1.1
[map-link-183]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.1.2
[map-link-184]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.1.3
[map-link-185]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.1.4
[map-link-186]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.2
[map-link-187]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.3.3
[map-link-188]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4
[map-link-189]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.1
[map-link-190]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.2
[map-link-191]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.3
[map-link-192]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.3.1
[map-link-193]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.3.2
[map-link-194]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.3.3
[map-link-195]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.3.4
[map-link-196]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.3.5
[map-link-197]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.3.6
[map-link-198]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.4
[map-link-199]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.5
[map-link-200]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.6
[map-link-201]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.7
[map-link-202]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.8
[map-link-203]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.8.1
[map-link-204]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.8.2
[map-link-205]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.8.3
[map-link-206]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.8.4
[map-link-207]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.4.9
[map-link-208]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.5
[map-link-209]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.6
[map-link-210]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.7
[map-link-211]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8
[map-link-212]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.1
[map-link-213]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.2
[map-link-214]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.3
[map-link-215]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.4
[map-link-216]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.5
[map-link-217]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.6
[map-link-218]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.7
[map-link-219]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.7.1
[map-link-220]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.8
[map-link-221]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.9
[map-link-222]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.8.10
[map-link-223]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.9
[map-link-224]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.9.1
[map-link-225]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.9.2
[map-link-226]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.9.3
[map-link-227]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10
[map-link-228]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.1
[map-link-229]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.2
[map-link-230]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.3
[map-link-231]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.4
[map-link-232]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.4.1
[map-link-233]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.10.4.2
[map-link-234]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html
[map-link-235]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1
[map-link-236]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.1
[map-link-237]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.1.1
[map-link-238]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.1.2
[map-link-239]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.1.3
[map-link-240]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.1.4
[map-link-241]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.2
[map-link-242]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.3
[map-link-243]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.4
[map-link-244]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.1.5
[map-link-245]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.2
[map-link-246]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.3
[map-link-247]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.3.1
[map-link-248]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4
[map-link-249]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4.1
[map-link-250]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4.1.1
[map-link-251]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4.1.2
[map-link-252]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4.1.3
[map-link-253]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4.2
[map-link-254]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.4.3
[map-link-255]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.5
[map-link-256]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6
[map-link-257]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.1
[map-link-258]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.2
[map-link-259]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.3
[map-link-260]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4
[map-link-261]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.1
[map-link-262]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.2
[map-link-263]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.3
[map-link-264]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.4
[map-link-265]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.5
[map-link-266]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.6
[map-link-267]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.7
[map-link-268]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.8
[map-link-269]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.6.4.9
[map-link-270]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.7
[map-link-271]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.7.1
[map-link-272]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.7.2
[map-link-273]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.7.3
[map-link-274]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.7.4
[map-link-275]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.7.5
[map-link-276]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.8
[map-link-277]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#jls-9.9
[map-link-278]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html
[map-link-279]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.1
[map-link-280]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.2
[map-link-281]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.3
[map-link-282]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.4
[map-link-283]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.5
[map-link-284]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.6
[map-link-285]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.7
[map-link-286]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.8
[map-link-287]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#jls-10.9
[map-link-288]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html
[map-link-289]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.1
[map-link-290]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.1.1
[map-link-291]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.1.2
[map-link-292]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.1.3
[map-link-293]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.2
[map-link-294]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.2.1
[map-link-295]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.2.2
[map-link-296]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.2.3
[map-link-297]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#jls-11.3
[map-link-298]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html
[map-link-299]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.1
[map-link-300]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.1.1
[map-link-301]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.1.2
[map-link-302]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.1.3
[map-link-303]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.1.4
[map-link-304]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.2
[map-link-305]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.2.1
[map-link-306]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.2.2
[map-link-307]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.3
[map-link-308]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.3.1
[map-link-309]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.3.2
[map-link-310]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.3.3
[map-link-311]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4
[map-link-312]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.1
[map-link-313]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.2
[map-link-314]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.5
[map-link-315]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.6
[map-link-316]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.6.1
[map-link-317]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.6.2
[map-link-318]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.7
[map-link-319]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.8
[map-link-320]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html
[map-link-321]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.1
[map-link-322]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.2
[map-link-323]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.3
[map-link-324]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4
[map-link-325]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.1
[map-link-326]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.2
[map-link-327]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.2.1
[map-link-328]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.2.2
[map-link-329]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.2.3
[map-link-330]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.3
[map-link-331]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.4
[map-link-332]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.5
[map-link-333]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.6
[map-link-334]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.7
[map-link-335]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.8
[map-link-336]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.9
[map-link-337]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.10
[map-link-338]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.11
[map-link-339]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.12
[map-link-340]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.13
[map-link-341]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.14
[map-link-342]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.15
[map-link-343]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.16
[map-link-344]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.17
[map-link-345]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.18
[map-link-346]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.19
[map-link-347]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.20
[map-link-348]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.21
[map-link-349]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.22
[map-link-350]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.23
[map-link-351]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.24
[map-link-352]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.25
[map-link-353]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.26
[map-link-354]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.4.27
[map-link-355]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5
[map-link-356]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.1
[map-link-357]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.2
[map-link-358]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.3
[map-link-359]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.4
[map-link-360]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.5
[map-link-361]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.6
[map-link-362]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.7
[map-link-363]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#jls-13.5.8
[map-link-364]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html
[map-link-365]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.1
[map-link-366]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.2
[map-link-367]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.3
[map-link-368]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.4
[map-link-369]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.4.1
[map-link-370]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.4.2
[map-link-371]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.5
[map-link-372]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.6
[map-link-373]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.7
[map-link-374]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.8
[map-link-375]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.9
[map-link-376]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.9.1
[map-link-377]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.9.2
[map-link-378]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.10
[map-link-379]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.11
[map-link-380]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.11.1
[map-link-381]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.11.1.1
[map-link-382]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.11.1.2
[map-link-383]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.11.2
[map-link-384]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.11.3
[map-link-385]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.12
[map-link-386]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.12.1
[map-link-387]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.13
[map-link-388]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.13.1
[map-link-389]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.14
[map-link-390]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.14.1
[map-link-391]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.14.1.1
[map-link-392]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.14.1.2
[map-link-393]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.14.1.3
[map-link-394]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.14.2
[map-link-395]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.15
[map-link-396]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.16
[map-link-397]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.17
[map-link-398]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.18
[map-link-399]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.19
[map-link-400]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20
[map-link-401]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.1
[map-link-402]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.2
[map-link-403]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.3
[map-link-404]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.3.1
[map-link-405]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.20.3.2
[map-link-406]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.21
[map-link-407]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.22
[map-link-408]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.30
[map-link-409]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.30.1
[map-link-410]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.30.2
[map-link-411]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.30.3
[map-link-412]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html
[map-link-413]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.1
[map-link-414]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.2
[map-link-415]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.3
[map-link-416]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.4
[map-link-417]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.5
[map-link-418]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.6
[map-link-419]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.7
[map-link-420]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.7.1
[map-link-421]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.7.2
[map-link-422]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.7.3
[map-link-423]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.7.4
[map-link-424]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.7.5
[map-link-425]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.8
[map-link-426]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.8.1
[map-link-427]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.8.2
[map-link-428]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.8.3
[map-link-429]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.8.4
[map-link-430]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.8.5
[map-link-431]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9
[map-link-432]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9.1
[map-link-433]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9.2
[map-link-434]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9.3
[map-link-435]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9.4
[map-link-436]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9.5
[map-link-437]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.9.5.1
[map-link-438]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.10
[map-link-439]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.10.1
[map-link-440]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.10.2
[map-link-441]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.10.3
[map-link-442]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.10.4
[map-link-443]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.11
[map-link-444]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.11.1
[map-link-445]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.11.2
[map-link-446]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12
[map-link-447]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.1
[map-link-448]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.2
[map-link-449]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.2.1
[map-link-450]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.2.2
[map-link-451]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.2.3
[map-link-452]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.2.4
[map-link-453]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.2.5
[map-link-454]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.2.6
[map-link-455]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.3
[map-link-456]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4
[map-link-457]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.1
[map-link-458]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.2
[map-link-459]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.3
[map-link-460]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.4
[map-link-461]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.5
[map-link-462]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.13
[map-link-463]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.13.1
[map-link-464]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.13.2
[map-link-465]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.13.3
[map-link-466]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.14
[map-link-467]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.14.1
[map-link-468]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.14.2
[map-link-469]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.14.3
[map-link-470]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15
[map-link-471]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15.1
[map-link-472]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15.2
[map-link-473]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15.3
[map-link-474]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15.4
[map-link-475]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15.5
[map-link-476]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15.6
[map-link-477]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.16
[map-link-478]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.17
[map-link-479]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.17.1
[map-link-480]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.17.2
[map-link-481]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.17.3
[map-link-482]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.18
[map-link-483]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.18.1
[map-link-484]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.18.2
[map-link-485]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.19
[map-link-486]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.20
[map-link-487]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.20.1
[map-link-488]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.20.2
[map-link-489]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21
[map-link-490]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21.1
[map-link-491]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21.2
[map-link-492]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21.3
[map-link-493]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.22
[map-link-494]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.22.1
[map-link-495]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.22.2
[map-link-496]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.23
[map-link-497]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.24
[map-link-498]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.25
[map-link-499]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.25.1
[map-link-500]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.25.2
[map-link-501]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.25.3
[map-link-502]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.26
[map-link-503]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.26.1
[map-link-504]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.26.2
[map-link-505]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.27
[map-link-506]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.27.1
[map-link-507]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.27.2
[map-link-508]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.27.3
[map-link-509]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.27.4
[map-link-510]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.28
[map-link-511]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.28.1
[map-link-512]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.28.2
[map-link-513]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.29
[map-link-514]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html
[map-link-515]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1
[map-link-516]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.1
[map-link-517]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.2
[map-link-518]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.3
[map-link-519]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.4
[map-link-520]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.5
[map-link-521]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.6
[map-link-522]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.7
[map-link-523]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.8
[map-link-524]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.9
[map-link-525]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.1.10
[map-link-526]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2
[map-link-527]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.1
[map-link-528]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.2
[map-link-529]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.3
[map-link-530]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.4
[map-link-531]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.5
[map-link-532]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.6
[map-link-533]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.7
[map-link-534]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.8
[map-link-535]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.9
[map-link-536]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.10
[map-link-537]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.11
[map-link-538]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.12
[map-link-539]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.12.1
[map-link-540]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.12.2
[map-link-541]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.13
[map-link-542]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.14
[map-link-543]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.2.15
[map-link-544]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.3
[map-link-545]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.4
[map-link-546]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.5
[map-link-547]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.6
[map-link-548]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.7
[map-link-549]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.8
[map-link-550]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#jls-16.9
[map-link-551]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html
[map-link-552]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.1
[map-link-553]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.2
[map-link-554]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.2.1
[map-link-555]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.2.2
[map-link-556]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.2.3
[map-link-557]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.2.4
[map-link-558]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.3
[map-link-559]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4
[map-link-560]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.1
[map-link-561]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.2
[map-link-562]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.3
[map-link-563]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.4
[map-link-564]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.5
[map-link-565]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.6
[map-link-566]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.7
[map-link-567]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.8
[map-link-568]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.4.9
[map-link-569]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.5
[map-link-570]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.5.1
[map-link-571]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.5.2
[map-link-572]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.5.3
[map-link-573]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.5.4
[map-link-574]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.6
[map-link-575]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#jls-17.7
[map-link-576]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html
[map-link-577]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.1
[map-link-578]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.1.1
[map-link-579]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.1.2
[map-link-580]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.1.3
[map-link-581]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.2
[map-link-582]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.2.1
[map-link-583]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.2.2
[map-link-584]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.2.3
[map-link-585]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.2.4
[map-link-586]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.2.5
[map-link-587]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.3
[map-link-588]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.3.1
[map-link-589]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.3.2
[map-link-590]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.4
[map-link-591]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5
[map-link-592]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5.1
[map-link-593]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5.2
[map-link-594]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5.2.1
[map-link-595]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5.2.2
[map-link-596]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5.3
[map-link-597]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5.4
[map-link-598]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#jls-18.5.5
[map-link-599]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-19.html
[map-link-600]: https://docs.oracle.com/javase/specs/jls/se25/html/spec-license.html
[map-link-601]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#d5e2003
[map-link-602]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#d5e2089
[map-link-603]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#d5e2099
[map-link-604]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#d5e2132
[map-link-605]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#d5e2156
[map-link-606]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e2550
[map-link-607]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e2832
[map-link-608]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e3040
[map-link-609]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e3082
[map-link-610]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e3281
[map-link-611]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e3632
[map-link-612]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e3641
[map-link-613]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e4013
[map-link-614]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e4016
[map-link-615]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e5293
[map-link-616]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e5586
[map-link-617]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e5623
[map-link-618]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e5758
[map-link-619]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#d5e5817
[map-link-620]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e5888
[map-link-621]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e5971
[map-link-622]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e6071
[map-link-623]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e6217
[map-link-624]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e6240
[map-link-625]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e7397
[map-link-626]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e7409
[map-link-627]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e7414
[map-link-628]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e8278
[map-link-629]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e8294
[map-link-630]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e8299
[map-link-631]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e8464
[map-link-632]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#d5e8469
[map-link-633]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e8849
[map-link-634]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e8871
[map-link-635]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e8885
[map-link-636]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e8910
[map-link-637]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e9274
[map-link-638]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e9284
[map-link-639]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e9855
[map-link-640]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10000
[map-link-641]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10062
[map-link-642]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10587
[map-link-643]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10732
[map-link-644]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10766
[map-link-645]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10854
[map-link-646]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10868
[map-link-647]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e10879
[map-link-648]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11003
[map-link-649]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11011
[map-link-650]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11038
[map-link-651]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11161
[map-link-652]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11200
[map-link-653]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11219
[map-link-654]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11240
[map-link-655]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11275
[map-link-656]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11401
[map-link-657]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11519
[map-link-658]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-6.html#d5e11561
[map-link-659]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e11932
[map-link-660]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12062
[map-link-661]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12079
[map-link-662]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12089
[map-link-663]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12098
[map-link-664]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12145
[map-link-665]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12266
[map-link-666]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12318
[map-link-667]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12348
[map-link-668]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12408
[map-link-669]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12423
[map-link-670]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12430
[map-link-671]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-7.html#d5e12637
[map-link-672]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13065
[map-link-673]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13097
[map-link-674]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13262
[map-link-675]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13265
[map-link-676]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13313
[map-link-677]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13453
[map-link-678]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13551
[map-link-679]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13575
[map-link-680]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13636
[map-link-681]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13671
[map-link-682]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13713
[map-link-683]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13739
[map-link-684]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e13758
[map-link-685]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14016
[map-link-686]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14045
[map-link-687]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14076
[map-link-688]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14093
[map-link-689]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14103
[map-link-690]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14303
[map-link-691]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14336
[map-link-692]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14400
[map-link-693]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14428
[map-link-694]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14462
[map-link-695]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14544
[map-link-696]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14567
[map-link-697]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14669
[map-link-698]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14678
[map-link-699]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14731
[map-link-700]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e14983
[map-link-701]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15067
[map-link-702]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15076
[map-link-703]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15187
[map-link-704]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15197
[map-link-705]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15412
[map-link-706]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15533
[map-link-707]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15738
[map-link-708]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15751
[map-link-709]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15843
[map-link-710]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15988
[map-link-711]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e15993
[map-link-712]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16009
[map-link-713]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16031
[map-link-714]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16106
[map-link-715]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16133
[map-link-716]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16159
[map-link-717]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16380
[map-link-718]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16533
[map-link-719]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16750
[map-link-720]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16758
[map-link-721]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16823
[map-link-722]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16833
[map-link-723]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e16864
[map-link-724]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e17060
[map-link-725]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e17065
[map-link-726]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e17134
[map-link-727]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e17140
[map-link-728]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e17151
[map-link-729]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#d5e17160
[map-link-730]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18183
[map-link-731]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18198
[map-link-732]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18231
[map-link-733]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18549
[map-link-734]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18749
[map-link-735]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18753
[map-link-736]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18757
[map-link-737]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18794
[map-link-738]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18927
[map-link-739]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e18968
[map-link-740]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e19028
[map-link-741]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e19656
[map-link-742]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e19680
[map-link-743]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e19707
[map-link-744]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e20039
[map-link-745]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e20063
[map-link-746]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e20101
[map-link-747]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e20379
[map-link-748]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-9.html#d5e20427
[map-link-749]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20507
[map-link-750]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20542
[map-link-751]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20596
[map-link-752]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20636
[map-link-753]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20711
[map-link-754]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20756
[map-link-755]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20764
[map-link-756]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20790
[map-link-757]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html#d5e20800
[map-link-758]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#d5e21242
[map-link-759]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-11.html#d5e21336
[map-link-760]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#d5e21710
[map-link-761]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#d5e21719
[map-link-762]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#d5e21730
[map-link-763]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#d5e21956
[map-link-764]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#d5e21997
[map-link-765]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#d5e22018
[map-link-766]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e22960
[map-link-767]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e22984
[map-link-768]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23012
[map-link-769]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23036
[map-link-770]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23061
[map-link-771]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23113
[map-link-772]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23166
[map-link-773]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23300
[map-link-774]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23326
[map-link-775]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23382
[map-link-776]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23508
[map-link-777]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-13.html#d5e23557
[map-link-778]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e23754
[map-link-779]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e23863
[map-link-780]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e23917
[map-link-781]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e24064
[map-link-782]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e25273
[map-link-783]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e25510
[map-link-784]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e25982
[map-link-785]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e25988
[map-link-786]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e26057
[map-link-787]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e26168
[map-link-788]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e26440
[map-link-789]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e26728
[map-link-790]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e26856
[map-link-791]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e27253
[map-link-792]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#d5e27651
[map-link-793]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e28431
[map-link-794]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e28442
[map-link-795]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e28456
[map-link-796]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e28478
[map-link-797]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e28524
[map-link-798]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e28533
[map-link-799]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e28813
[map-link-800]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e29602
[map-link-801]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e29813
[map-link-802]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e29830
[map-link-803]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e29875
[map-link-804]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e29944
[map-link-805]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e29964
[map-link-806]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e29973
[map-link-807]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e30084
[map-link-808]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e30118
[map-link-809]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e30242
[map-link-810]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e30529
[map-link-811]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e30576
[map-link-812]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e30587
[map-link-813]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e31870
[map-link-814]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e31883
[map-link-815]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e32208
[map-link-816]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e32236
[map-link-817]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e32284
[map-link-818]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e33520
[map-link-819]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e33565
[map-link-820]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e33614
[map-link-821]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e33633
[map-link-822]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e34016
[map-link-823]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e36095
[map-link-824]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e36182
[map-link-825]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e36190
[map-link-826]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#d5e36918
[map-link-827]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#d5e36969
[map-link-828]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#d5e36995
[map-link-829]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-16.html#d5e37014
[map-link-830]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#d5e39525
[map-link-831]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#d5e39998
[map-link-832]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#d5e40423
[map-link-833]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#d5e40565
[map-link-834]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#d5e40592
[map-link-835]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#d5e40732
[map-link-836]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html#d5e40794
[map-link-837]: https://docs.oracle.com/javase/specs/jls/se25/html/jls-18.html#d5e44747
[map-link-838]: https://docs.oracle.com/javase/specs/jls/se25/html/index.html
[map-link-839]: https://docs.oracle.com/javase/specs/jls/se25/html/spec-frontmatter.html
[map-link-840]: https://docs.oracle.com/javase/specs/jls/se25/html/spec-license.html
