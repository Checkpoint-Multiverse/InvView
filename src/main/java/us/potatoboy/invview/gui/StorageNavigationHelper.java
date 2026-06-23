package us.potatoboy.invview.gui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StorageNavigationHelper {

    private static final Pattern STORAGE_PATTERN =
            Pattern.compile("^adminpanel_storage(\\d+)_(\\d+)$");

    private static final Pattern PLAYER_STORAGE_PATTERN =
            Pattern.compile("^adminpanel_storage(\\d+)P_(.+)$");

    private static final Pattern PLAYER_BASE_PATTERN =
            Pattern.compile("^adminpanel_storage(.+)$");

    public static String getNext(String title) {

        Matcher storageMatcher = STORAGE_PATTERN.matcher(title);

        if (storageMatcher.matches()) {
            int current = Integer.parseInt(storageMatcher.group(1));
            String page = storageMatcher.group(2);

            if (current < 9) {
                return "adminpanel_storage" + (current + 1) + "_" + page;
            }

            return null;
        }

        Matcher playerStorageMatcher = PLAYER_STORAGE_PATTERN.matcher(title);

        if (playerStorageMatcher.matches()) {
            int current = Integer.parseInt(playerStorageMatcher.group(1));
            String playerName = playerStorageMatcher.group(2);

            if (current < 3) {
                return "adminpanel_storage" + (current + 1) + "P_" + playerName;
            }

            return null;
        }

        Matcher playerBaseMatcher = PLAYER_BASE_PATTERN.matcher(title);

        if (playerBaseMatcher.matches()
                && !title.contains("P_")
                && !STORAGE_PATTERN.matcher(title).matches()) {

            String playerName = playerBaseMatcher.group(1);

            return "adminpanel_storage1P_" + playerName;
        }

        return null;
    }

    public static String getPrevious(String title) {

        Matcher storageMatcher = STORAGE_PATTERN.matcher(title);

        if (storageMatcher.matches()) {
            int current = Integer.parseInt(storageMatcher.group(1));
            String page = storageMatcher.group(2);

            if (current > 0) {
                return "adminpanel_storage" + (current - 1) + "_" + page;
            }

            return null;
        }

        Matcher playerStorageMatcher = PLAYER_STORAGE_PATTERN.matcher(title);

        if (playerStorageMatcher.matches()) {
            int current = Integer.parseInt(playerStorageMatcher.group(1));
            String playerName = playerStorageMatcher.group(2);

            if (current == 1) {
                return "adminpanel_storage" + playerName;
            }

            if (current > 1) {
                return "adminpanel_storage" + (current - 1) + "P_" + playerName;
            }
        }

        return null;
    }
}