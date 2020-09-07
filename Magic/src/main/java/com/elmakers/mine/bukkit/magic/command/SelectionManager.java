package com.elmakers.mine.bukkit.magic.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.elmakers.mine.bukkit.api.magic.Locatable;
import com.elmakers.mine.bukkit.magic.MagicController;
import com.elmakers.mine.bukkit.utility.TextUtils;

public abstract class SelectionManager<T extends Locatable> {
    protected enum ListType {
        ACTIVE,
        TARGET,
        SELECTED,
        INACTIVE
    }

    protected final MagicController controller;
    private final Selection<T> consoleSelection = new Selection<>();
    private final Map<UUID, Selection<T>> selections = new HashMap<>();
    private int rowsPerPage = 8;

    public SelectionManager(MagicController controller) {
        this.controller = controller;
    }

    @Nullable
    public T getSelected(CommandSender sender) {
        Selection<T> selection = getSelection(sender);
        return selection == null ? null : selection.getSelected();
    }

    @Nullable
    private Selection<T> getSelection(CommandSender sender) {
        Selection<T> selection = consoleSelection;
        Player player = sender instanceof Player ? (Player)sender : null;
        if (player != null) {
            selection = selections.get(player.getUniqueId());
        }
        return selection;
    }

    @Nonnull
    private Selection<T> createSelection(CommandSender sender) {
        if (sender instanceof Player) {
            Selection<T> selection = getSelection(sender);
            if (selection == null) {
                selection = new Selection<>();
                selections.put(((Player)sender).getUniqueId(), selection);
            }
            return selection;
        }
        return consoleSelection;
    }

    public void clearSelection(CommandSender sender) {
        Selection<T> selection = getSelection(sender);
        if (selection != null) {
            selection.setSelected(null);
        }
    }

    public void setSelection(CommandSender sender, T select) {
        Selection<T> selection = createSelection(sender);
        selection.setSelected(select);
    }

    @Nonnull
    protected abstract Collection<T> getAll();

    @Nonnull
    protected abstract String getTypeNamePlural();

    protected abstract void showListItem(CommandSender sender, T item, ListType listType);

    @Nullable
    protected abstract T getTarget(CommandSender sender, List<T> sorted);

    @Nullable
    public List<T> getList(CommandSender sender) {
        Selection<T> selection = getSelection(sender);
        return selection == null ? null : selection.getList();
    }

    @Nonnull
    public Selection<T> updateList(CommandSender sender) {
        Location location = null;
        if (sender instanceof Player) {
            location = ((Player)sender).getEyeLocation();
        }
        Collection<T> all = getAll();
        List<T> sorted;
        if (location != null) {
            sorted = getSorted(all, location);
        } else {
            sorted = new ArrayList<>(all);
        }
        Selection<T> selection = createSelection(sender);
        selection.setList(sorted);
        return selection;
    }

    public void list(CommandSender sender, String[] args) {
        int page = 0;
        String pageNumber = "?";
        if (args.length > 0) {
            try {
                pageNumber = args[0];
                page = Integer.parseInt(args[0]) - 1;
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Invalid page number: " + ChatColor.WHITE + args[0]);
                return;
            }
        }

        Selection<T> selection = updateList(sender);
        List<T> sorted = selection.getList();
        if (sorted.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No automata to list");
            return;
        }
        int start = page * rowsPerPage;
        int end = start + rowsPerPage;
        int pages = (int)Math.ceil((double)sorted.size() / rowsPerPage) + 1;
        if (start < 0 || start > sorted.size()) {
            sender.sendMessage(ChatColor.RED + "Invalid page number: " + ChatColor.WHITE + pageNumber
                + ChatColor.GRAY + "/" + ChatColor.GOLD + pages);
            return;
        }
        sender.sendMessage(ChatColor.AQUA + "Total " + getTypeNamePlural() + ": " + ChatColor.DARK_AQUA + sorted.size());
        ListType listType;
        ChatColor color;
        T target = getTarget(sender, sorted);
        T selected = selection.getSelected();
        end = Math.min(end, sorted.size());
        for (int i = start; i < end; i++) {
            T item = sorted.get(i);
            if (item == selected) {
                listType = ListType.SELECTED;
                color = ChatColor.GOLD;
            } else if (item == target) {
                listType = ListType.TARGET;
                color = ChatColor.AQUA;
            } else if (item.isActive()) {
                listType = ListType.ACTIVE;
                color = ChatColor.LIGHT_PURPLE;
            } else {
                listType = ListType.INACTIVE;
                color = ChatColor.GRAY;
            }
            showListItem(sender, item, listType);
            String message = ChatColor.WHITE + Integer.toString(i + 1) + ChatColor.GRAY + ": "
                + color + item.getName() + ChatColor.DARK_PURPLE
                + getDistanceMessage(sender, item);
            sender.sendMessage(message);
        }

        if (sorted.size() > rowsPerPage) {
            sender.sendMessage("  " + ChatColor.GRAY + "Page " + ChatColor.YELLOW
                + (page + 1) + ChatColor.GRAY + "/" + ChatColor.GOLD + pages);
        }
    }

    @Nonnull
    public String getDistanceMessage(CommandSender sender, Locatable item) {
        String message = "";
        if (sender instanceof Player) {
            Location location = ((Player)sender).getEyeLocation();
            Location itemLocation = item.getLocation();
            if (location.getWorld().equals(itemLocation.getWorld())) {
                double distance = location.distance(itemLocation);
                message = ChatColor.GRAY + " (" + ChatColor.WHITE + TextUtils.printNumber(distance, 1)
                    + ChatColor.BLUE + " blocks away" + ChatColor.GRAY + ")";
            }
        }
        return message;
    }

    private List<T> getSorted(Collection<T> list, Location location) {
        List<T> sorted = new ArrayList<>(list);
        Collections.sort(sorted, new Comparator<T>() {
            @Override
            public int compare(Locatable a, Locatable b) {
                boolean aInWorld = location.getWorld().equals(a.getLocation().getWorld());
                boolean bInWorld = location.getWorld().equals(b.getLocation().getWorld());
                if (aInWorld && !bInWorld) return -1;
                if (!aInWorld && bInWorld) return 1;
                if (!aInWorld) return 0;
                double aDistance = location.distanceSquared(a.getLocation());
                double bDistance = location.distanceSquared(b.getLocation());
                if (aDistance < bDistance) {
                    return -1;
                } else if (aDistance > bDistance) {
                    return 1;
                }
                return 0;
            }
        });
        return sorted;
    }
}