package org.aquilagraph.build;

import java.io.IOException;
import java.lang.System.Logger.Level;

/** Coordinates captured-input compilation and verified publication under the local build lease. */
public final class CompilationPreparation {
  private CompilationPreparation() {
  }

  /**
   * Prepares one complete production or test batch using the selected JDK.
   *
   * @param arguments
   *          root, batch, source/resource/output paths, force flag and literal compiler arguments
   * @throws IOException
   *           if inputs, ownership or publication cannot be verified
   * @throws InterruptedException
   *           if compiler execution is interrupted
   */
  public static void main(String[] arguments) throws IOException, InterruptedException {
    BuildAdmission.main(new String[]{"default"});
    var request = CompilationRequest.parse(arguments);
    var exit = prepare(request);
    if (exit != 0) {
      System.exit(exit);
    }
  }

  static int prepare(CompilationRequest request) throws IOException, InterruptedException {
    var inputs = new CompilationInputs(request);
    var publication = new CompilationPublication(request);
    var matchingInputs = inputs.hasMatchingManifest();
    if (matchingInputs && publication.reusable(inputs.identity())) {
      inputs.requireUnchanged();
      System.getLogger(CompilationPreparation.class.getName()).log(Level.INFO,
          "Compilation " + request.batch() + ": reused verified output; javac was not invoked");
      return 0;
    }
    if (!matchingInputs) {
      System.getLogger(CompilationPreparation.class.getName()).log(Level.INFO,
          "Compilation " + request.batch() + ": rebuild because no matching captured-input manifest exists");
    }
    publication.invalidate();
    inputs.capture();
    var staged = request.state().resolve("output");
    PreparationFiles.clear(request.root(), staged);
    PreparationFiles.copy(request.state().resolve("capture/resources"), staged);
    System.getLogger(CompilationPreparation.class.getName()).log(Level.INFO,
        "Compilation " + request.batch() + ": compiling captured inputs; provenance: " + request.state().resolve("provenance.properties"));
    var exit = CompilationCompiler.execute(request, inputs);
    if (exit != 0) {
      return exit;
    }
    inputs.requireCaptured();
    inputs.requireUnchanged();
    publication.publish(inputs.identity());
    try {
      inputs.requireUnchanged();
    } catch (IOException exception) {
      publication.invalidate();
      throw exception;
    }
    PreparationFiles.clear(request.root(), staged);
    return 0;
  }
}
