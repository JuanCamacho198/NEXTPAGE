use super::domain::{Bookmark, Highlight, ReadingProgress};
use super::dto::{BookmarkDto, HighlightDto, ReadingProgressDto};

pub fn progress_dto_to_domain(dto: ReadingProgressDto) -> ReadingProgress {
    ReadingProgress {
        id: dto.id,
        book_id: dto.book_id,
        locator: dto.cfi_location,
        percent: dto.percentage,
        updated_at: dto.updated_at,
    }
}

pub fn progress_domain_to_dto(domain: ReadingProgress) -> ReadingProgressDto {
    ReadingProgressDto {
        id: domain.id,
        book_id: domain.book_id,
        cfi_location: domain.locator,
        percentage: domain.percent,
        updated_at: domain.updated_at,
    }
}

pub fn highlight_dto_to_domain(dto: HighlightDto) -> Highlight {
    Highlight {
        id: dto.id,
        book_id: dto.book_id,
        color: dto.color,
        text: dto.text,
        page: dto.page,
        created_at: dto.created_at,
        updated_at: dto.updated_at,
    }
}

pub fn bookmark_dto_to_domain(dto: BookmarkDto) -> Bookmark {
    Bookmark {
        id: dto.id,
        book_id: dto.book_id,
        page: dto.page,
        position: dto.position,
        title: dto.title,
        created_at: dto.created_at,
    }
}
