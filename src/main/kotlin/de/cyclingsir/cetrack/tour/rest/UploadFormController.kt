package de.cyclingsir.cetrack.tour.rest

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod


/**
 * Initially created on 2/5/23.
 */
@Controller
class UploadFormController {

    @RequestMapping(
        method = [RequestMethod.GET],
        value = ["/"],
        produces = ["application/html"]
    )
    fun uploadFileForm(model: Model): String {
        return "uploadForm"
    }
}
