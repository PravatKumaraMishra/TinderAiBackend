package me.pravat.tinder_ai_backend;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static List<String> selfieTypes() {
        return List.of(
                "closeup with head and partial shoulders",
                "Reflection in a mirror",
                "action selfie, person in motion",
                "candid",
                "standing in nature",
                "sitting on a chair",
                "indoor",
                "outdoor");
    }

    public static List<String> generateMyersBriggsTypes() {
        List<String> myersBriggsPersonalityTypes = new ArrayList<>();

        String[] dimensions = {
                "E,I", // Extraversion or Introversion
                "S,N", // Sensing or Intuition
                "T,F", // Thinking or Feeling
                "J,P" // Judging or Perceiving
        };

        // Generate all combinations
        for (String e : dimensions[0].split(",")) {
            for (String s : dimensions[1].split(",")) {
                for (String t : dimensions[2].split(",")) {
                    for (String j : dimensions[3].split(",")) {
                        myersBriggsPersonalityTypes.add(e + s + t + j);
                    }
                }
            }
        }

        return myersBriggsPersonalityTypes;
    }
}