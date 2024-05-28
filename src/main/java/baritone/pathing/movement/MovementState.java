/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.pathing.movement;

import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.*;

public class MovementState {
    List<String> excludedStackTraces = new ArrayList<>();
    private MovementStatus status;
    private MovementTarget target = new MovementTarget();
    private final Map<Input, Boolean> inputState = new HashMap<>();

    public MovementState setStatus(MovementStatus status) {
        this.status = status;
        return this;
    }

    public MovementStatus getStatus() {
        return status;
    }

    public MovementTarget getTarget() {
        //logStackTraceToFile("C:/Users/hzant/OneDrive/Documents/LocalGPT/movement-state-fetched.txt");
        return this.target;
    }

    private void logStackTraceToFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                Files.createFile(file.toPath());
            } catch (IOException e) {
                System.err.println("An error occurred while creating the file: " + e.getMessage());
                e.printStackTrace();
            }
        }
        try (FileWriter fileWriter = new FileWriter(filePath, true);
             PrintWriter printWriter = new PrintWriter(fileWriter)) {
            StringBuilder stringBuilder = new StringBuilder();
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                stringBuilder.append(element);
            }
            //Remove the first line
            stringBuilder.delete(0, stringBuilder.indexOf("\n") + 1);
            if (excludedStackTraces.contains(stringBuilder.toString())) {
                return;
            }
            excludedStackTraces.add(stringBuilder.toString());
            printWriter.println();
            printWriter.println("Movement state queried");
            printWriter.println("Stack trace at " + java.time.LocalDateTime.now() + ":");
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                printWriter.println(element);
            }
            printWriter.println();
        } catch (IOException e) {
            System.err.println("An error occurred while writing the stack trace to the file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public MovementState setTarget(MovementTarget target) {
        //this.target = target;
        return this;
    }

    public MovementState setInput(Input input, boolean forced) {
        //this.inputState.put(input, forced);
        return this;
    }

    public Map<Input, Boolean> getInputStates() {
        return this.inputState;
    }

    public static class MovementTarget {

        /**
         * Yaw and pitch angles that must be matched
         */
        public Rotation rotation;

        /**
         * Whether or not this target must force rotations.
         * <p>
         * {@code true} if we're trying to place or break blocks, {@code false} if we're trying to look at the movement location
         */
        private boolean forceRotations;

        public MovementTarget() {
            this(null, false);
        }

        public MovementTarget(Rotation rotation, boolean forceRotations) {
            this.rotation = rotation;
            this.forceRotations = forceRotations;
        }

        public final Optional<Rotation> getRotation() {
            return Optional.ofNullable(this.rotation);
        }

        public boolean hasToForceRotations() {
            return this.forceRotations;
        }
    }
}
