use super::domain::DomainMarker;
use super::dto::DtoCompatibilityMarker;

pub fn dto_to_domain(dto: DtoCompatibilityMarker) -> DomainMarker {
    DomainMarker {
        sample_field: dto.sample_field,
    }
}

pub fn domain_to_dto(domain: DomainMarker) -> DtoCompatibilityMarker {
    DtoCompatibilityMarker {
        sample_field: domain.sample_field,
    }
}
