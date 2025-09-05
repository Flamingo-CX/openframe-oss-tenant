pub struct VersionComparator;

// TODO: use during version update feature
impl VersionComparator {

    pub fn compare(&self, v1: &str, v2: &str) -> std::cmp::Ordering {
        let v1 = semver::Version::parse(&self.normalize(v1)).unwrap();
        let v2 = semver::Version::parse(&self.normalize(v2)).unwrap();
        v1.cmp(&v2)
    }

    fn normalize(&self, v: &str) -> String {
        let parts: Vec<&str> = v.split('.').collect();
        match parts.len() {
            1 => format!("{}.0.0", parts[0]),
            2 => format!("{}.0", v),
            _ => v.to_string(),
        }
    }
}
