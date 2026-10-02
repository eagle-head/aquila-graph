package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class CompilerFileManagerTest {
  @Test
  void givenRepeatedAndDistinctLoaders_whenManagerCloses_thenEachOwnedResourceClosesOnceInReverseOrder() throws IOException {
    // Arrange
    var closed = new ArrayList<String>();
    var first = new ObservedLoader("first", closed, null);
    var second = new ObservedLoader("second", closed, null);
    try (var fixture = new ManagerFixture(List.of(first, first, second), closed, null)) {
      var manager = new CompilerFileManager(fixture.manager);
      assertSame(first, manager.getClassLoader(StandardLocation.CLASS_PATH));
      assertSame(first, manager.getClassLoader(StandardLocation.CLASS_PATH));
      assertSame(second, manager.getClassLoader(StandardLocation.CLASS_PATH));

      // Act
      manager.close();

      // Assert
      assertEquals(List.of("second", "first", "manager"), closed);
    }
  }

  @Test
  void givenLoaderAndManagerCloseFailures_whenManagerCloses_thenOriginalCheckedFailureRetainsEveryLaterFailure() throws IOException {
    // Arrange
    var closed = new ArrayList<String>();
    var primary = new IOException("second loader close");
    var later = new IOException("first loader close");
    var managerFailure = new IOException("manager close");
    var first = new ObservedLoader("first", closed, later);
    var second = new ObservedLoader("second", closed, primary);
    try (var fixture = new ManagerFixture(List.of(first, second), closed, managerFailure)) {
      var manager = new CompilerFileManager(fixture.manager);
      manager.getClassLoader(StandardLocation.CLASS_PATH);
      manager.getClassLoader(StandardLocation.CLASS_PATH);

      // Act
      var actual = assertThrows(IOException.class, manager::close);

      // Assert
      assertSame(primary, actual);
      assertArrayEquals(new Throwable[] {later, managerFailure}, actual.getSuppressed());
      assertEquals(List.of("second", "first", "manager"), closed);
    }
  }

  @Test
  void givenUncheckedLoaderCloseFailure_whenManagerCloses_thenItsFamilyAndIdentitySurviveAndOtherResourcesStillClose() throws IOException {
    for (var primary : List.of(new IllegalArgumentException("loader close"), new AssertionError("loader close"))) {
      // Arrange
      var closed = new ArrayList<String>();
      var later = new IOException("earlier loader close");
      var first = new ObservedLoader("first", closed, later);
      var second = new ObservedLoader("second", closed, primary);
      try (var fixture = new ManagerFixture(List.of(first, second), closed, null)) {
        var manager = new CompilerFileManager(fixture.manager);
        manager.getClassLoader(StandardLocation.CLASS_PATH);
        manager.getClassLoader(StandardLocation.CLASS_PATH);

        // Act
        var actual = assertThrows(primary.getClass(), manager::close);

        // Assert
        assertSame(primary, actual);
        assertArrayEquals(new Throwable[] {later}, actual.getSuppressed());
        assertEquals(List.of("second", "first", "manager"), closed);
      }
    }
  }

  private static final class ObservedLoader extends ClassLoader implements Closeable {
    private final String name;
    private final List<String> closed;
    private final Throwable failure;

    private ObservedLoader(String name, List<String> closed, Throwable failure) {
      this.name = name;
      this.closed = closed;
      this.failure = failure;
    }

    @Override
    public void close() throws IOException {
      closed.add(name);
      switch (failure) {
        case null -> { }
        case IOException checked -> throw checked;
        case RuntimeException runtime -> throw runtime;
        case Error error -> throw error;
        default -> throw new IllegalStateException(failure);
      }
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ObservedLoader;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }

  private static final class ManagerFixture implements Closeable {
    private final StandardJavaFileManager delegate = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null);
    private final StandardJavaFileManager manager;
    private int loaderIndex;

    private ManagerFixture(List<ObservedLoader> loaders, List<String> closed, IOException failure) {
      manager = (StandardJavaFileManager) Proxy.newProxyInstance(StandardJavaFileManager.class.getClassLoader(),
          new Class<?>[] {StandardJavaFileManager.class}, (_, method, arguments) -> {
            if (method.getName().equals("getClassLoader")) {
              return loaders.get(loaderIndex++);
            }
            if (method.getName().equals("close")) {
              closed.add("manager");
              delegate.close();
              if (failure != null) {
                throw failure;
              }
              return null;
            }
            try {
              return method.invoke(delegate, arguments);
            } catch (InvocationTargetException exception) {
              throw exception.getCause();
            }
          });
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
