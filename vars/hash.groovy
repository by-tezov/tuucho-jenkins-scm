@NonCPS
def call(Map data) {
    def input = data.values().join('|')
    def digest = java.security.MessageDigest.getInstance("SHA-256")
    def hashBytes = digest.digest(input.getBytes("UTF-8"))
    return hashBytes.collect { String.format("%02x", it) }.join()
}