// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.actions;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.actions.Artifact.ArtifactExpander;
import com.google.devtools.build.lib.util.Fingerprint;
import javax.annotation.Nullable;

/** A representation of a list of arguments. */
public abstract class CommandLine {
  private static class EmptyCommandLine extends CommandLine {
    @Override
    public Iterable<String> arguments() {
      return ImmutableList.of();
    }
  }

  public static final CommandLine EMPTY = new EmptyCommandLine();

  /** Returns the command line. */
  public abstract Iterable<String> arguments()
      throws CommandLineExpansionException, InterruptedException;

  /**
   * Returns the evaluated command line with enclosed artifacts expanded by {@code artifactExpander}
   * at execution time.
   *
   * <p>By default, this method just delegates to {@link #arguments()}, without performing any
   * artifact expansion. Subclasses should override this method if they contain TreeArtifacts and
   * need to expand them for proper argument evaluation.
   */
  public Iterable<String> arguments(ArtifactExpander artifactExpander, PathMapper pathMapper)
      throws CommandLineExpansionException, InterruptedException {
    return arguments();
  }

  /**
   * Adds the command line to the provided {@link Fingerprint}.
   *
   * <p>Some of the implementations may require the to expand provided directory in order to produce
   * a unique key. Consequently, the result of calling this function can be different depending on
   * whether the {@link ArtifactExpander} is provided. Moreover, without it, the produced key may
   * not always be unique.
   */
  public void addToFingerprint(
      ActionKeyContext actionKeyContext,
      @Nullable ArtifactExpander artifactExpander,
      Fingerprint fingerprint)
      throws CommandLineExpansionException, InterruptedException {
    for (String s : arguments()) {
      fingerprint.addString(s);
    }
  }

  private static final class SimpleCommandLine extends CommandLine {
    private final ImmutableList<String> args;

    SimpleCommandLine(ImmutableList<String> args) {
      this.args = args;
    }

    @Override
    public ImmutableList<String> arguments() {
      return args;
    }
  }

  /** Returns a {@link CommandLine} backed by the given list of arguments. */
  public static CommandLine of(ImmutableList<String> arguments) {
    return arguments.isEmpty() ? CommandLine.EMPTY : new SimpleCommandLine(arguments);
  }

  private static final class SuffixedCommandLine extends CommandLine {
    private final ImmutableList<String> executableArgs;
    private final CommandLine commandLine;

    SuffixedCommandLine(ImmutableList<String> executableArgs, CommandLine commandLine) {
      this.executableArgs = executableArgs;
      this.commandLine = commandLine;
    }

    @Override
    public Iterable<String> arguments() throws CommandLineExpansionException, InterruptedException {
      return Iterables.concat(commandLine.arguments(), executableArgs);
    }

    @Override
    public Iterable<String> arguments(ArtifactExpander artifactExpander, PathMapper pathMapper)
        throws CommandLineExpansionException, InterruptedException {
      return Iterables.concat(commandLine.arguments(artifactExpander, pathMapper), executableArgs);
    }
  }

  /**
   * Returns a {@link CommandLine} that is constructed by appending the {@code args} to {@code
   * commandLine}.
   */
  public static CommandLine concat(CommandLine commandLine, ImmutableList<String> args) {
    if (args.isEmpty()) {
      return commandLine;
    }
    if (commandLine == EMPTY) {
      return CommandLine.of(args);
    }
    return new SuffixedCommandLine(args, commandLine);
  }

  /**
   * This helps when debugging Blaze code that uses {@link CommandLine}s, as you can see their
   * content directly in the variable inspector.
   */
  @Override
  public String toString() {
    try {
      return Joiner.on(' ').join(arguments());
    } catch (CommandLineExpansionException e) {
      return "Error in expanding command line";
    } catch (InterruptedException unused) {
      Thread.currentThread().interrupt();
      return "Interrupted while expanding command line";
    }
  }
}
