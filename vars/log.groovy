def success(message) {
    echo "\u001B[32m${message}\u001B[0m"
}

def info(message) {
    echo "\u001B[34m${message}\u001B[0m"
}

def warning(message) {
    echo "\u001B[33m${message}\u001B[0m"
}

def error(message) {
    echo "\u001B[31m${message}\u001B[0m"
}