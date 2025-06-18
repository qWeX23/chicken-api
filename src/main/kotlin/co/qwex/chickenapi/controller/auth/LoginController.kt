package co.qwex.chickenapi.controller.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class LoginController(
    @Value("\${supabase.url:https://your-project.supabase.co}")
    private val supabaseUrl: String,
    @Value("\${supabase.anon-key:YOUR_ANON_KEY}")
    private val anonKey: String,
) {
    @GetMapping("/login")
    fun login(model: Model): String {
        model.addAttribute("supabaseUrl", supabaseUrl)
        model.addAttribute("supabaseAnonKey", anonKey)
        return "login"
    }
}
