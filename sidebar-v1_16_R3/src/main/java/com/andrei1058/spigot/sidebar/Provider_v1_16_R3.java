package com.andrei1058.spigot.sidebar;

import java.util.Collection;

@SuppressWarnings("unused")
public class Provider_v1_16_R3 extends SidebarProvider {

    @Override
    public Sidebar createSidebar(SidebarLine title, Collection<SidebarLine> lines, Collection<PlaceholderProvider> placeholderProviders) {
        return new Sidebar_v1_16_R3(title, lines, placeholderProviders);
    }
}