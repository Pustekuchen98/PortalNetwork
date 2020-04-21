/*
 * PortalNetwork - Portals for Players
 * Copyright (C) 2020 PortalNetwork Developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package au.com.grieve.portalnetwork;

import lombok.Getter;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.*;

public class PortalManager {
    private final JavaPlugin plugin;

    // Portals
    @Getter
    private final List<Portal> portals = new ArrayList<>();

    // Location Map
    private final Hashtable<BlockVector, Portal> indexLocation = new Hashtable<>();

    public PortalManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        // Portal Data
        Config portalConfig = new Config(plugin.getDataFolder() + "/" + "portal-data.yml");
        try {
            portalConfig.load();
        } catch (IOException | InvalidConfigurationException ignored) {
        }

        // Initialize all portals
        Map<Portal, Integer> dialed = new HashMap<>();
        List<Portal> invalid = new ArrayList<>();

        ConfigurationSection portalsData = portalConfig.getConfigurationSection("portals");
        if (portalsData != null) {
            for (String key : portalsData.getKeys(false)) {
                ConfigurationSection portalData = portalsData.getConfigurationSection(key);
                if (portalData == null) {
                    continue;
                }

                Portal portal = new Portal(
                        this,
                        portalData.getLocation("location"),
                        Portal.PortalType.valueOf(portalData.getString("portal_type"))
                );

                // Update valid portals, ignore invalid so we don't accidentally dial them later
                if (portalData.getBoolean("valid")) {
                    portal.update();
                } else {
                    invalid.add(portal);
                }

                if (portalData.contains("dialed")) {
                    dialed.put(portal, portalData.getInt("dialed"));
                }

                portals.add(portal);
                reindexPortal(portal);
            }
        }

        // Dial Portals
        for (Map.Entry<Portal, Integer> dialedPortal : dialed.entrySet()) {
            dialedPortal.getKey().dial(dialedPortal.getValue());
        }

        // Update invalids
        for (Portal portal : invalid) {
            portal.update();
        }
    }

    public void reload() {
        for (Portal portal : portals) {
            portal.destroy();
        }
        portals.clear();
        indexLocation.clear();

        // Load Data
        load();
    }

    public void save() {
        Config portalConfig = new Config(plugin.getDataFolder() + "/" + "portal-data.yml");
        ConfigurationSection portalsData = portalConfig.createSection("portals");
        for (int i = 0; i < portals.size(); i++) {
            Portal portal = portals.get(i);
            ConfigurationSection portalData = portalsData.createSection(Integer.toString(i));

            if (portal.getDialed() != null) {
                portalData.set("dialed", portal.getDialed().getAddress());
            }
            portalData.set("portal_type", portal.getPortalType().toString());
            portalData.set("location", portal.getLocation());
            portalData.set("valid", portal.isValid());
        }

        portalConfig.save();
    }

    /**
     * Create a new portal
     */
    public Portal createPortal(Location location, Portal.PortalType portalType) {
        Portal portal = new Portal(this, location, portalType);
        portals.add(portal);
        portal.update();
        reindexPortal(portal);
        save();

        return portal;
    }

    public void removePortal(Portal portal) {
        portals.remove(portal);
        indexLocation.values().removeIf(v -> v.equals(portal));
    }

    public void reindexPortal(Portal portal) {
        indexLocation.values().removeIf(v -> v.equals(portal));
        for (Iterator<Location> it = portal.getPortalIterator(); it.hasNext(); ) {
            Location loc = it.next();
            indexLocation.put(loc.toVector().toBlockVector(), portal);
        }

        for (Iterator<Location> it = portal.getPortalFrameIterator(); it.hasNext(); ) {
            Location loc = it.next();
            indexLocation.put(loc.toVector().toBlockVector(), portal);
        }

        for (Iterator<Location> it = portal.getPortalBaseIterator(); it.hasNext(); ) {
            Location loc = it.next();
            indexLocation.put(loc.toVector().toBlockVector(), portal);
        }

        indexLocation.put(portal.getLocation().toVector().toBlockVector(), portal);
    }

    /**
     * Find a portal
     */
    public Portal find(Integer network, Integer address, Boolean valid) {
        for (Portal portal : portals) {
            if (valid != null && portal.isValid() != valid) {
                continue;
            }

            if (!Objects.equals(portal.getNetwork(), network)) {
                continue;
            }

            if (!Objects.equals(portal.getAddress(), address)) {
                continue;
            }

            return portal;
        }
        return null;
    }

    public Portal find(Integer network, Integer address) {
        return find(network, address, null);
    }

    /**
     * Get a portal at location
     */
    public Portal find(@NonNull Location location, Boolean valid, int distance) {
        Vector search = location.toVector();

        for (int x = -distance; x <= distance; x++) {
            for (int y = -distance; y <= distance; y++) {
                for (int z = -distance; z <= distance; z++) {
                    Portal portal = indexLocation.get(search.clone().add(new Vector(x, y, z)).toBlockVector());
                    if (portal != null) {
                        if (valid == null || valid == portal.isValid()) {
                            return portal;
                        }
                    }
                }
            }
        }

        return null;
    }

    public Portal find(@NonNull Location location) {
        return find(location, null, 0);
    }

    public Portal find(@NonNull Location location, int distance) {
        return find(location, null, distance);
    }

}
