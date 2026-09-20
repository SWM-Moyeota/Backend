package team.codingforest.moyeota.chat.domain.search;

import java.util.List;

public interface MessageSearchIndex {
    List<Hit> search(MessageSearchQuery query);
    record Hit(Long id, String indexedContent, String highlight) {}
}
