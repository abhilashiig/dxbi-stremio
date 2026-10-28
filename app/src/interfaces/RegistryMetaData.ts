interface RegistryMetaData {
    name: string,
    author: string,
    version: string,
    repo: string,
    download: string,
    sha256: string,
    description?: string,
    preview?: string,
    unchecked?: boolean
}

export default RegistryMetaData;