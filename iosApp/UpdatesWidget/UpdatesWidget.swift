import WidgetKit
import SwiftUI

struct MangaUpdateWidgetEntry: TimelineEntry {
    let date: Date
    let recentChapters: [(mangaTitle: String, chapterName: String)]
}

struct MihonUpdatesProvider: TimelineProvider {
    func placeholder(in context: Context) -> MangaUpdateWidgetEntry {
        MangaUpdateWidgetEntry(
            date: Date(),
            recentChapters: [
                ("Omniscient Reader's Viewpoint", "Episode 230"),
                ("Solo Leveling: Ragnarok", "Chapter 48"),
                ("The Greatest Estate Developer", "Episode 175")
            ]
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (MangaUpdateWidgetEntry) -> Void) {
        completion(placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MangaUpdateWidgetEntry>) -> Void) {
        let entry = placeholder(in: context)
        let nextRefresh = Calendar.current.date(byAdding: .hour, value: 1, to: Date()) ?? Date()
        completion(Timeline(entries: [entry], policy: .after(nextRefresh)))
    }
}

struct MihonUpdatesWidgetEntryView: View {
    var entry: MihonUpdatesProvider.Entry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Mihon Updates")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
                Spacer()
                Text("\(entry.recentChapters.count) new")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            ForEach(0..<min(entry.recentChapters.count, 3), id: \.self) { index in
                let item = entry.recentChapters[index]
                VStack(alignment: .leading, spacing: 1) {
                    Text(item.mangaTitle)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .lineLimit(1)
                    Text(item.chapterName)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
        }
        .padding(12)
    }
}

@main
struct UpdatesWidget: Widget {
    let kind: String = "MihonUpdatesWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: MihonUpdatesProvider()) { entry in
            MihonUpdatesWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Mihon Recent Updates")
        .description("View your latest Manhwa, Webtoon, and Manga chapter updates.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

