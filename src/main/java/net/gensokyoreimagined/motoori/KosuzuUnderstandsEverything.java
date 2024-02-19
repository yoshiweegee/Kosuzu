// Kosuzu Copyright (C) 2024 Gensokyo Reimagined
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package net.gensokyoreimagined.motoori;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.server.MinecraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Logger;

public class KosuzuUnderstandsEverything implements Listener {
    private final Logger logger;
    private final KosuzuRemembersEverything database;
    private final KosuzuKnowsWhereYouLive geolocation;
    private final KosuzuParsesEverything parser;

    public KosuzuUnderstandsEverything(Kosuzu kosuzu) {
        logger = kosuzu.getLogger();
        database = kosuzu.database;
        geolocation = new KosuzuKnowsWhereYouLive(kosuzu);
        parser = new KosuzuParsesEverything(kosuzu);

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(kosuzu, ListenerPriority.NORMAL, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                onClientChatSend(event);
            }
        });

        manager.addPacketListener(new PacketAdapter(kosuzu, ListenerPriority.NORMAL, PacketType.Play.Server.SYSTEM_CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                onServerChatSend(event);
            }
        });
    }

    private void onClientChatSend(PacketEvent event) {
        var player = event.getPlayer();
        var packet = event.getPacket();

        var signature = (ClientboundPlayerChatPacket) packet.getMessageSignatures().getTarget();
        var message = Objects.requireNonNullElseGet(signature.unsignedContent(), () -> net.minecraft.network.chat.Component.literal(signature.body().content()));

        var boundChatType = signature.chatType().resolve(MinecraftServer.getServer().registryAccess());

        if (boundChatType.isEmpty()) {
            logger.warning("Don't know how to process packet with unknown ChatType " + signature.chatType().chatType());
            return;
        }

        // Use the vanilla decorated content and then convert it to Adventure API
        var decoratedContent = boundChatType.orElseThrow().decorate(message);
        var text = decoratedContent.getString();
        var component = Component.text(text);
        // Only to get the JSON
        var json = JSONComponentSerializer.json().serialize(component);

        var uuid = database.addMessage(json, message.getString());

        var newComponent = component.hoverEvent(
                Component
                    .text(database.getTranslation("translate.hover", database.getUserDefaultLanguage(player.getUniqueId())))
                    .color(NamedTextColor.GRAY)
            )
            .clickEvent(
                ClickEvent.runCommand("/kosuzu translate " + uuid.toString())
            );

        var newJson = JSONComponentSerializer.json().serialize(newComponent);

        packet = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
        packet.getChatComponents().write(0, WrappedChatComponent.fromJson(newJson));
        event.setPacket(packet);
    }

    private void onServerChatSend(PacketEvent event) {
        var player = event.getPlayer();
        var packet = event.getPacket();
        var message = packet.getChatComponents().read(0);

        var json = message.getJson();
        var component = JSONComponentSerializer.json().deserialize(json); // Adventure API from raw JSON
        var text = parser.getTextMessage(component, player);

        if (text != null) {
            var uuid = database.addMessage(json, text);

            var newComponent = component.hoverEvent(
                    Component
                        .text(database.getTranslation("translate.hover", database.getUserDefaultLanguage(player.getUniqueId())))
                        .color(NamedTextColor.GRAY)
                )
                .clickEvent(
                    ClickEvent.runCommand("/kosuzu translate " + uuid.toString())
                );

            var newJson = JSONComponentSerializer.json().serialize(newComponent);
            packet.getChatComponents().write(0, WrappedChatComponent.fromJson(newJson));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        var name = player.getName();


        if (database.isNewUser(uuid, name)) {
            var country = geolocation.getCountryCode(player);
            if (Objects.equals(country, "CN")) country = "ZH"; // Special case for China
            if (Objects.equals(country, "TW")) country = "ZH"; // Special case for Taiwan
            if (Objects.equals(country, "HK")) country = "ZH"; // Special case for Hong Kong
            if (Objects.equals(country, "JP")) country = "JA"; // Special case for Japan
            if (Objects.equals(country, "GB")) country = "EN-GB"; // Special case for England
            if (Objects.equals(country, "US")) country = "EN-US"; // Special case for United States

            if (country != null) {
                // Extra searching for languages
                var languages = database.getLanguages();
                String finalCountry = country;
                country = languages.stream().map(KosuzuDatabaseModels.Language::code).filter(code -> code.toUpperCase().contains(finalCountry.toUpperCase())).findFirst().orElse(null);
            }

            if (country != null) {
                try {
                    database.setUserDefaultLanguage(uuid, country);
                } catch (Exception e) {
                    logger.warning("Failed to set default language for " + name + " (" + uuid + ") to " + country + ": " + e.getMessage());
                }
            }

            player.sendMessage(
                Kosuzu.HEADER
                    .append(Component.text(database.getTranslation("welcome.first", country).replace("%username%", name), NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text(database.getTranslation("welcome.second", country), NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text(database.getTranslation("welcome.third", country), NamedTextColor.GRAY))
            );
        }
    }
}
