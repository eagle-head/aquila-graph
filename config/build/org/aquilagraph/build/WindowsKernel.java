package org.aquilagraph.build;

import java.io.IOException;
import java.io.Serial;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.aquilagraph.build.WindowsJobObject.Child;

/** Owns checked Win32 ABI bindings and exact native handle operations. */
record WindowsKernel(Arena library, MemoryLayout callState, Map<String, NativeCall> functions) {
  private static final String CREATE_FILE_FUNCTION = "CreateFileW";
  private static final String SET_FILE_INFORMATION_FUNCTION = "SetFileInformationByHandle";
  private static final int FILE_DELETE_ACCESS = 0x10000;
  private static final int FILE_SHARE_ALL = 7;
  private static final int OPEN_EXISTING = 3;
  private static final int OPEN_REPARSE_POINT = 0x200000;
  private static final int FILE_DISPOSITION_INFO_EX = 21;
  private static final int DISPOSITION_DELETE = 1;
  private static final int DISPOSITION_POSIX_SEMANTICS = 2;
  private static final int DISPOSITION_IGNORE_READONLY = 0x10;
  private static final long INVALID_HANDLE_VALUE = -1;
  private static final int ERROR_ALREADY_EXISTS = 183;
  private static final int ERROR_FILE_NOT_FOUND = 2;
  private static final int ERROR_INSUFFICIENT_BUFFER = 122;
  private static final int ERROR_INVALID_PARAMETER = 87;
  private static final int ERROR_ACCESS_DENIED = 5;
  private static final int JOB_OBJECT_QUERY = 4;
  private static final int JOB_OBJECT_TERMINATE = 8;
  private static final int ACCOUNTING_BYTES = 48;
  private static final int ACTIVE_PROCESSES_OFFSET = 40;
  private static final int WAIT_TIMEOUT = 0x102;
  private static final String STANDARD_HANDLE_FUNCTION = "GetStdHandle";
  private static final int CANCELLATION_EXIT = 130;
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
  private static final int PROCESS_SYNCHRONIZE = 0x100000;
  private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
  private static final long WINDOWS_EPOCH_MILLISECONDS = 11644473600000L;
  private static final long MAX_PROCESS_ID = 0xffff_ffffL;
  private static final long FILE_TIME_TICKS_PER_MILLISECOND = 10000;

  static WindowsKernel instance() throws IOException {
    if (!WindowsKernel.class.getModule().isNativeAccessEnabled()) {
      throw new IOException("Windows native operations require --enable-native-access=ALL-UNNAMED");
    }
    return Holder.BINDINGS;
  }

  @SuppressWarnings("restricted")
  private static WindowsKernel bind() {
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
        Map.entry("OpenProcess", FunctionDescriptor.of(address, integer, integer, integer)),
        Map.entry("GetProcessTimes", FunctionDescriptor.of(integer, address, address, address, address, address)),
        Map.entry(CREATE_FILE_FUNCTION, FunctionDescriptor.of(address, address, integer, integer, address, integer, integer, address)),
        Map.entry(SET_FILE_INFORMATION_FUNCTION, FunctionDescriptor.of(integer, address, integer, address, integer)),
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
    return new WindowsKernel(library, Option.captureStateLayout(), Map.copyOf(functions));
  }

  private static MethodHandle optionalResult() {
    try {
      return MethodHandles.publicLookup().findStatic(Optional.class, "ofNullable", MethodType.methodType(Optional.class, Object.class));
    } catch (ReflectiveOperationException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  MemorySegment create(String name) throws IOException {
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

  Optional<MemorySegment> open(String name) throws IOException {
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

  void killWhenLastHandleCloses(MemorySegment job) throws IOException {
    try (var arena = Arena.ofConfined()) {
      var limits = arena.allocate(EXTENDED_LIMIT_BYTES, ValueLayout.JAVA_LONG.byteAlignment());
      limits.set(ValueLayout.JAVA_INT, LIMIT_FLAGS_OFFSET, KILL_ON_JOB_CLOSE);
      requireSuccess("SetInformationJobObject", job, EXTENDED_LIMIT_INFORMATION, limits, EXTENDED_LIMIT_BYTES);
    }
  }

  int activeProcesses(MemorySegment job) throws IOException {
    try (var arena = Arena.ofConfined()) {
      var accounting = arena.allocate(ACCOUNTING_BYTES, ValueLayout.JAVA_LONG.byteAlignment());
      requireSuccess("QueryInformationJobObject", job, 1, accounting, ACCOUNTING_BYTES, MemorySegment.NULL);
      return accounting.get(ValueLayout.JAVA_INT, ACTIVE_PROCESSES_OFFSET);
    }
  }

  Child start(MemorySegment job, Path java, Path arguments, Path directory) throws IOException {
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

  void terminate(MemorySegment job) throws IOException {
    requireSuccess("TerminateJobObject", job, CANCELLATION_EXIT);
  }

  Optional<Child> openProcess(ProcessIdentity identity) throws IOException {
    if (identity.pid() <= 0 || identity.pid() > MAX_PROCESS_ID) {
      throw new IOException("Recorded Windows process identity is out of range: " + identity.pid());
    }
    var result = invoke("OpenProcess", PROCESS_SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION, 0, (int) identity.pid());
    var handle = (MemorySegment) result.value().orElseThrow();
    if (handle.address() == 0) {
      if (result.error() == ERROR_INVALID_PARAMETER) {
        return Optional.empty();
      }
      if (result.error() == ERROR_ACCESS_DENIED) {
        throw new ProcessAccessDeniedException(identity.pid());
      }
      throw failed("OpenProcess", result.error());
    }
    try (var process = new ProcessAcquisition(new Child(handle, identity.pid()))) {
      try (var arena = Arena.ofConfined()) {
        var created = arena.allocate(ValueLayout.JAVA_LONG);
        var exited = arena.allocate(ValueLayout.JAVA_LONG);
        var kernel = arena.allocate(ValueLayout.JAVA_LONG);
        var user = arena.allocate(ValueLayout.JAVA_LONG);
        requireSuccess("GetProcessTimes", handle, created, exited, kernel, user);
        var started = Instant
            .ofEpochMilli(created.get(ValueLayout.JAVA_LONG, 0) / FILE_TIME_TICKS_PER_MILLISECOND - WINDOWS_EPOCH_MILLISECONDS);
        if (!started.equals(identity.started())) {
          return Optional.empty();
        }
        return Optional.of(process.transfer());
      }
    }
  }

  boolean completed(MemorySegment process) throws IOException {
    var result = invokeInt("WaitForSingleObject", process, 0);
    if (result == 0) {
      return true;
    }
    if (result == WAIT_TIMEOUT) {
      return false;
    }
    throw new IOException("Native process termination observation failed: " + result);
  }

  void close(MemorySegment job) throws IOException {
    requireSuccess("CloseHandle", job);
  }

  void deleteFile(Path path) throws IOException {
    try (var arena = Arena.ofConfined()) {
      var opened = invoke(CREATE_FILE_FUNCTION, wideString(arena, extendedPath(path)), FILE_DELETE_ACCESS, FILE_SHARE_ALL,
          MemorySegment.NULL, OPEN_EXISTING, OPEN_REPARSE_POINT, MemorySegment.NULL);
      var handle = (MemorySegment) opened.value().orElseThrow();
      if (handle.address() == INVALID_HANDLE_VALUE) {
        throw failed(CREATE_FILE_FUNCTION, opened.error());
      }
      try (var file = new FileHandle(this, handle)) {
        var disposition = arena.allocate(ValueLayout.JAVA_INT);
        disposition.set(ValueLayout.JAVA_INT, 0, DISPOSITION_DELETE | DISPOSITION_POSIX_SEMANTICS | DISPOSITION_IGNORE_READONLY);
        requireSuccess(SET_FILE_INFORMATION_FUNCTION, file.handle(), FILE_DISPOSITION_INFO_EX, disposition,
            Math.toIntExact(ValueLayout.JAVA_INT.byteSize()));
      }
    }
  }

  private record FileHandle(WindowsKernel kernel, MemorySegment handle) implements AutoCloseable {
    @Override
    public void close() throws IOException {
      kernel.close(handle);
    }
  }

  private static String extendedPath(Path path) {
    var absolute = path.toAbsolutePath().toString();
    if (absolute.startsWith("\\\\?\\")) {
      return absolute;
    }
    return absolute.startsWith("\\\\") ? "\\\\?\\UNC\\" + absolute.substring(2) : "\\\\?\\" + absolute;
  }

  int invokeInt(String operation, Object... arguments) {
    return (Integer) invoke(operation, arguments).value().orElseThrow();
  }

  void requireSuccess(String operation, Object... arguments) throws IOException {
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

  static final class ProcessAccessDeniedException extends IOException {
    @Serial
    private static final long serialVersionUID = 1L;

    ProcessAccessDeniedException(long pid) {
      super("OpenProcess failed with Windows error 5 for PID " + pid);
    }
  }

  private static final class Holder {
    private static final WindowsKernel BINDINGS = bind();

    private Holder() {
    }
  }

  private static final class ProcessAcquisition implements AutoCloseable {
    private final Child process;
    private boolean transferred;

    ProcessAcquisition(Child process) {
      this.process = process;
    }

    Child transfer() {
      transferred = true;
      return process;
    }

    @Override
    public void close() throws IOException {
      if (!transferred) {
        process.close();
      }
    }
  }
  private record NativeResult(Optional<Object> value, int error) {
  }
}
