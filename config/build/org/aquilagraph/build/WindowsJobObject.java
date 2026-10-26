package org.aquilagraph.build;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.Linker.Option;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Native job membership survives parent exit and cannot be escaped by ordinary child creation. */
final class WindowsJobObject implements AutoCloseable {
  private static final int ERROR_ALREADY_EXISTS = 183;
  private static final int ERROR_FILE_NOT_FOUND = 2;
  private static final int ERROR_INSUFFICIENT_BUFFER = 122;
  private static final int JOB_OBJECT_QUERY = 4;
  private static final int JOB_OBJECT_TERMINATE = 8;
  private static final int ACCOUNTING_BYTES = 48;
  private static final int ACTIVE_PROCESSES_OFFSET = 40;
  private static final int WAIT_TIMEOUT = 0x102;
  private static final String STANDARD_HANDLE_FUNCTION = "GetStdHandle";
  private static final int CANCELLATION_EXIT = 130;
  private final MemorySegment handle;

  private WindowsJobObject(MemorySegment handle) {
    this.handle = handle;
  }

  static WindowsJobObject create(Properties intent) throws IOException {
    requireAccess();
    try (var acquisition = new Acquisition(NativeBindings.instance().create(name(intent)))) {
      NativeBindings.instance().killWhenLastHandleCloses(acquisition.handle());
      return acquisition.transfer();
    }
  }

  static Optional<WindowsJobObject> open(Properties intent) throws IOException {
    requireAccess();
    return NativeBindings.instance().open(name(intent)).map(WindowsJobObject::new);
  }

  Child start(Path java, Path arguments, Path directory) throws IOException {
    return NativeBindings.instance().start(handle, java, arguments, directory);
  }

  void terminateAndObserveEmpty() throws IOException {
    var interrupted = Thread.interrupted();
    try {
      NativeBindings.instance().terminate(handle);
      var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      while (NativeBindings.instance().activeProcesses(handle) != 0) {
        if (System.nanoTime() >= deadline) {
          throw new IOException("Owned Windows job closure remains unresolved");
        }
        try {
          TimeUnit.MILLISECONDS.sleep(25);
        } catch (InterruptedException exception) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void requireAccess() throws IOException {
    if (!WindowsJobObject.class.getModule().isNativeAccessEnabled()) {
      throw new IOException("Windows descendant containment requires --enable-native-access=ALL-UNNAMED");
    }
  }

  private static String name(Properties intent) throws IOException {
    return "Local\\AquilaBuild-" + BuildFiles.required(intent, "nonce") + "-" + BuildFiles.required(intent, "reservation");
  }

  @Override
  public void close() throws IOException {
    NativeBindings.instance().close(handle);
  }

  private static final class Acquisition implements AutoCloseable {
    private Optional<WindowsJobObject> job;

    Acquisition(MemorySegment handle) {
      job = Optional.of(new WindowsJobObject(handle));
    }

    MemorySegment handle() {
      return job.orElseThrow().handle;
    }

    WindowsJobObject transfer() {
      var result = job.orElseThrow();
      job = Optional.empty();
      return result;
    }

    @Override
    public void close() throws IOException {
      if (job.isPresent()) {
        job.orElseThrow().close();
      }
    }
  }

  static final class Child implements AutoCloseable {
    private final MemorySegment process;
    private final long pid;

    private Child(MemorySegment process, long pid) {
      this.process = process;
      this.pid = pid;
    }

    ProcessHandle handle() throws IOException {
      return ProcessHandle.of(pid).orElseThrow(() -> new IOException("The created contained process is no longer observable: " + pid));
    }

    boolean awaitSlice() throws IOException, InterruptedException {
      if (Thread.interrupted()) {
        throw new InterruptedException("The contained Windows process wait was interrupted");
      }
      var result = NativeBindings.instance().invokeInt("WaitForSingleObject", process, 100);
      if (result == 0) {
        return true;
      }
      if (result == WAIT_TIMEOUT) {
        return false;
      }
      throw new IOException("The contained Windows process wait failed: " + result);
    }

    int exitValue() throws IOException {
      try (var arena = Arena.ofConfined()) {
        var result = arena.allocate(ValueLayout.JAVA_INT);
        NativeBindings.instance().requireSuccess("GetExitCodeProcess", process, result);
        return result.get(ValueLayout.JAVA_INT, 0);
      }
    }

    @Override
    public void close() throws IOException {
      NativeBindings.instance().close(process);
    }
  }

  private record NativeBindings(Arena library, MemoryLayout callState, Map<String, NativeCall> functions) {
    private static final int EXTENDED_STARTUP_INFO_BYTES = 112;
    private static final int STARTUP_FLAGS_OFFSET = 60;
    private static final int STANDARD_INPUT_OFFSET = 80;
    private static final int STANDARD_OUTPUT_OFFSET = 88;
    private static final int STANDARD_ERROR_OFFSET = 96;
    private static final int ATTRIBUTES_OFFSET = 104;
    private static final int PROCESS_INFORMATION_BYTES = 24;
    private static final int PROCESS_ID_OFFSET = 16;
    private static final int EXTENDED_LIMIT_BYTES = 144;
    private static final int LIMIT_FLAGS_OFFSET = 16;
    private static final int KILL_ON_JOB_CLOSE = 0x2000;
    private static final int EXTENDED_LIMIT_INFORMATION = 9;
    private static final long JOB_LIST_ATTRIBUTE = 0x2000D;
    private static final int EXTENDED_STARTUP_PRESENT = 0x80000;
    private static final int USE_STANDARD_HANDLES = 0x100;
    private static final int STANDARD_INPUT = -10;
    private static final int STANDARD_OUTPUT = -11;
    private static final int STANDARD_ERROR = -12;
    private static final int JOB_OBJECT_ASSIGN_PROCESS = 1;

    private static NativeBindings instance() {
      return Holder.BINDINGS;
    }

    @SuppressWarnings("restricted")
    private static NativeBindings bind() {
      var library = Arena.ofAuto();
      var symbols = SymbolLookup.libraryLookup("kernel32", library);
      var linker = Linker.nativeLinker();
      var address = ValueLayout.ADDRESS;
      var integer = ValueLayout.JAVA_INT;
      var size = ValueLayout.JAVA_LONG;
      var descriptors = Map.ofEntries(Map.entry("CreateJobObjectW", FunctionDescriptor.of(address, address, address)),
          Map.entry("OpenJobObjectW", FunctionDescriptor.of(address, integer, integer, address)),
          Map.entry("QueryInformationJobObject", FunctionDescriptor.of(integer, address, integer, address, integer, address)),
          Map.entry("SetInformationJobObject", FunctionDescriptor.of(integer, address, integer, address, integer)),
          Map.entry("TerminateJobObject", FunctionDescriptor.of(integer, address, integer)),
          Map.entry("CloseHandle", FunctionDescriptor.of(integer, address)),
          Map.entry("InitializeProcThreadAttributeList", FunctionDescriptor.of(integer, address, integer, integer, address)),
          Map.entry("UpdateProcThreadAttribute", FunctionDescriptor.of(integer, address, integer, size, address, size, address, address)),
          Map.entry("DeleteProcThreadAttributeList", FunctionDescriptor.ofVoid(address)),
          Map.entry("CreateProcessW",
              FunctionDescriptor.of(integer, address, address, address, address, integer, integer, address, address, address, address)),
          Map.entry(STANDARD_HANDLE_FUNCTION, FunctionDescriptor.of(address, integer)),
          Map.entry("WaitForSingleObject", FunctionDescriptor.of(integer, address, integer)),
          Map.entry("GetExitCodeProcess", FunctionDescriptor.of(integer, address, address)));
      var functions = new HashMap<String, NativeCall>();
      for (var entry : descriptors.entrySet()) {
        var nativeMethod = linker.downcallHandle(symbols.findOrThrow(entry.getKey()), entry.getValue(),
            Option.captureCallState("GetLastError"));
        var spread = nativeMethod.asSpreader(Object[].class, nativeMethod.type().parameterCount())
            .asType(MethodType.methodType(Object.class, Object[].class));
        var optional = MethodHandles.filterReturnValue(spread, optionalResult());
        functions.put(entry.getKey(), MethodHandleProxies.asInterfaceInstance(NativeCall.class, optional));
      }
      return new NativeBindings(library, Option.captureStateLayout(), Map.copyOf(functions));
    }

    private static MethodHandle optionalResult() {
      try {
        return MethodHandles.publicLookup().findStatic(Optional.class, "ofNullable", MethodType.methodType(Optional.class, Object.class));
      } catch (ReflectiveOperationException exception) {
        throw new ExceptionInInitializerError(exception);
      }
    }

    private MemorySegment create(String name) throws IOException {
      try (var arena = Arena.ofConfined()) {
        var result = invoke("CreateJobObjectW", MemorySegment.NULL, wideString(arena, name));
        var handle = (MemorySegment) result.value().orElseThrow();
        if (handle.address() == 0) {
          throw failed("CreateJobObjectW", result.error());
        }
        if (result.error() == ERROR_ALREADY_EXISTS) {
          close(handle);
          throw new IOException("The new Windows job name already exists: " + name);
        }
        return handle;
      }
    }

    private Optional<MemorySegment> open(String name) throws IOException {
      try (var arena = Arena.ofConfined()) {
        var result = invoke("OpenJobObjectW", JOB_OBJECT_QUERY | JOB_OBJECT_TERMINATE | JOB_OBJECT_ASSIGN_PROCESS, 0,
            wideString(arena, name));
        var handle = (MemorySegment) result.value().orElseThrow();
        if (handle.address() != 0) {
          return Optional.of(handle);
        }
        if (result.error() == ERROR_FILE_NOT_FOUND) {
          return Optional.empty();
        }
        throw failed("OpenJobObjectW", result.error());
      }
    }

    private void killWhenLastHandleCloses(MemorySegment job) throws IOException {
      try (var arena = Arena.ofConfined()) {
        var limits = arena.allocate(EXTENDED_LIMIT_BYTES, ValueLayout.JAVA_LONG.byteAlignment());
        limits.set(ValueLayout.JAVA_INT, LIMIT_FLAGS_OFFSET, KILL_ON_JOB_CLOSE);
        requireSuccess("SetInformationJobObject", job, EXTENDED_LIMIT_INFORMATION, limits, EXTENDED_LIMIT_BYTES);
      }
    }

    private int activeProcesses(MemorySegment job) throws IOException {
      try (var arena = Arena.ofConfined()) {
        var accounting = arena.allocate(ACCOUNTING_BYTES, ValueLayout.JAVA_LONG.byteAlignment());
        requireSuccess("QueryInformationJobObject", job, 1, accounting, ACCOUNTING_BYTES, MemorySegment.NULL);
        return accounting.get(ValueLayout.JAVA_INT, ACTIVE_PROCESSES_OFFSET);
      }
    }

    private Child start(MemorySegment job, Path java, Path arguments, Path directory) throws IOException {
      try (var arena = Arena.ofConfined()) {
        var size = arena.allocate(ValueLayout.JAVA_LONG);
        var sizing = invoke("InitializeProcThreadAttributeList", MemorySegment.NULL, 1, 0, size);
        if (((Integer) sizing.value().orElseThrow()) != 0 || sizing.error() != ERROR_INSUFFICIENT_BUFFER) {
          throw failed("InitializeProcThreadAttributeList sizing", sizing.error());
        }
        var bytes = size.get(ValueLayout.JAVA_LONG, 0);
        if (bytes <= 0 || bytes > 64 * 1024) {
          throw new IOException("The native startup attribute size is unavailable or excessive: " + bytes);
        }
        var attributes = arena.allocate(bytes, ValueLayout.ADDRESS.byteAlignment());
        requireSuccess("InitializeProcThreadAttributeList", attributes, 1, 0, size);
        try {
          var jobs = arena.allocate(ValueLayout.ADDRESS);
          jobs.set(ValueLayout.ADDRESS, 0, job);
          requireSuccess("UpdateProcThreadAttribute", attributes, 0, JOB_LIST_ATTRIBUTE, jobs, ValueLayout.ADDRESS.byteSize(),
              MemorySegment.NULL, MemorySegment.NULL);
          var startup = arena.allocate(EXTENDED_STARTUP_INFO_BYTES, ValueLayout.ADDRESS.byteAlignment());
          startup.set(ValueLayout.JAVA_INT, 0, EXTENDED_STARTUP_INFO_BYTES);
          startup.set(ValueLayout.JAVA_INT, STARTUP_FLAGS_OFFSET, USE_STANDARD_HANDLES);
          startup.set(ValueLayout.ADDRESS, STANDARD_INPUT_OFFSET,
              (MemorySegment) invoke(STANDARD_HANDLE_FUNCTION, STANDARD_INPUT).value().orElseThrow());
          startup.set(ValueLayout.ADDRESS, STANDARD_OUTPUT_OFFSET,
              (MemorySegment) invoke(STANDARD_HANDLE_FUNCTION, STANDARD_OUTPUT).value().orElseThrow());
          startup.set(ValueLayout.ADDRESS, STANDARD_ERROR_OFFSET,
              (MemorySegment) invoke(STANDARD_HANDLE_FUNCTION, STANDARD_ERROR).value().orElseThrow());
          startup.set(ValueLayout.ADDRESS, ATTRIBUTES_OFFSET, attributes);
          var information = arena.allocate(PROCESS_INFORMATION_BYTES, ValueLayout.ADDRESS.byteAlignment());
          var command = "\"" + java + "\" \"@" + arguments + "\"";
          requireSuccess("CreateProcessW", wideString(arena, java.toString()), wideString(arena, command), MemorySegment.NULL,
              MemorySegment.NULL, 1, EXTENDED_STARTUP_PRESENT, MemorySegment.NULL, wideString(arena, directory.toString()), startup,
              information);
          var process = information.get(ValueLayout.ADDRESS, 0);
          try {
            close(information.get(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize()));
          } catch (IOException exception) {
            close(process);
            throw exception;
          }
          return new Child(process, Integer.toUnsignedLong(information.get(ValueLayout.JAVA_INT, PROCESS_ID_OFFSET)));
        } finally {
          invoke("DeleteProcThreadAttributeList", attributes);
        }
      }
    }

    private void terminate(MemorySegment job) throws IOException {
      requireSuccess("TerminateJobObject", job, CANCELLATION_EXIT);
    }

    private void close(MemorySegment job) throws IOException {
      requireSuccess("CloseHandle", job);
    }

    private int invokeInt(String operation, Object... arguments) {
      return (Integer) invoke(operation, arguments).value().orElseThrow();
    }

    private void requireSuccess(String operation, Object... arguments) throws IOException {
      var result = invoke(operation, arguments);
      if (((Integer) result.value().orElseThrow()) == 0) {
        throw failed(operation, result.error());
      }
    }

    private NativeResult invoke(String operation, Object... arguments) {
      try (var arena = Arena.ofConfined()) {
        var state = arena.allocate(callState);
        var parameters = new ArrayList<Object>(List.of(state));
        parameters.addAll(List.of(arguments));
        var method = Objects.requireNonNull(functions.get(operation), "The native operation must have a declared binding");
        var value = method.invoke(parameters.toArray());
        var error = state.get(ValueLayout.JAVA_INT, callState.byteOffset(PathElement.groupElement("GetLastError")));
        return new NativeResult(value, error);
      }
    }

    private static MemorySegment wideString(Arena arena, String value) {
      return arena.allocateFrom(ValueLayout.JAVA_BYTE, (value + '\0').getBytes(StandardCharsets.UTF_16LE));
    }

    private static IOException failed(String operation, int error) {
      return new IOException(operation + " failed with Windows error " + error);
    }

    private static final class Holder {
      private static final NativeBindings BINDINGS = bind();

      private Holder() {
      }
    }
  }

  private record NativeResult(Optional<Object> value, int error) {
  }
}
