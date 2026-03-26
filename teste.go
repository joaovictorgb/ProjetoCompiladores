package main

import "fmt"

func main() {
    var y string = "Hello World!"
    var x int
    fmt.Scanln(&x)
    var a string

    if ( (5 > 3) == (5 <= 3) ) {
        x = 10
    } else if ( (5 * 3) > 12 && (x == 1) ) {
        x = 2
        fmt.Println("Teste")
    } else {
        fmt.Println("OK")
    }

    for _, letra := range palavra {
        fmt.Println(letra)
        y = y + "2"
        letra = "a"
    }
}

func minhaFuncao(nome string, x bool, numero int) float64 {
    var resultado float64 = 3 * (2.5)
    for resultado > 2 {
        minhaFuncao("teste", (25 != 24), 100)
        resultado = resultado - 1
    }
    return resultado
}